package com.avdbuddy

import androidx.compose.runtime.*
import androidx.compose.ui.graphics.toComposeImageBitmap
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.awt.Desktop
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection
import java.io.File
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.util.concurrent.atomic.AtomicBoolean

class AppState(val scope: CoroutineScope, private val store: SettingsStore = SettingsStore(),
               initial: AppSettings = store.load(), private val executor: CommandExecutor = ProcessExecutor(),
               val display: DeviceDisplay = AdbDeviceDisplay(),
               private val storagePlanner: (CreateSpec, AppSettings) -> StoragePlan = ::planDeviceStorage) {
    var settings by mutableStateOf(initial); private set
    var devices by mutableStateOf(emptyList<Device>()); private set
    var running by mutableStateOf(emptyMap<String, String>()); private set
    var selected by mutableStateOf(setOf<String>())
    var previewName by mutableStateOf<String?>(null)
    val previewDevice get() = devices.firstOrNull { it.name == previewName } ?: selectedDevices.firstOrNull() ?: devices.firstOrNull()
    val frames = mutableStateMapOf<String, androidx.compose.ui.graphics.ImageBitmap>()
    val frameRotations = mutableStateMapOf<String, Int>()
    val displayErrors = mutableStateMapOf<String, String>()
    var starting by mutableStateOf(setOf<String>()); private set
    fun selectPreview(device: Device) { previewName = device.name; selected = setOf(device.name) }
    suspend fun observeDisplays(): Unit = coroutineScope {
        launch {
            val loaded = mutableSetOf<String>()
            snapshotFlow { devices.map { it.directory.absolutePath } }.collect { paths ->
                loaded.retainAll(paths.toSet())
                frames.keys.retainAll(paths.toSet())
                frameRotations.keys.retainAll(paths.toSet())
                devices.forEach { device ->
                    val key = device.directory.absolutePath
                    if (loaded.add(key)) {
                        val image = withContext(Dispatchers.IO) { display.load(device)?.toComposeImageBitmap() }
                        if (image != null && key !in frames) frames[key] = image
                    }
                }
            }
        }
        snapshotFlow {
            val device = previewDevice
            Triple(settings, device?.directory?.absolutePath, device?.let { running[it.name] })
        }.collectLatest { (currentSettings, path, serial) ->
            val device = devices.firstOrNull { it.directory.absolutePath == path } ?: return@collectLatest
            if (serial == null) return@collectLatest
            while (isActive) {
                try {
                    display.stream(currentSettings, device, serial).collect { frame ->
                        frames[device.directory.absolutePath] = frame.bitmap
                        frameRotations[device.directory.absolutePath] = frame.rotation
                        displayErrors.remove(device.name)
                    }
                } catch (e: Exception) {
                    if (e is CancellationException) throw e
                    displayErrors[device.name] = e.message ?: "Could not connect to the device screen."
                }
                delay(1000)
            }
        }
    }
    private val inputMutex = Mutex()
    val rotations = mutableStateMapOf<String, EmulatorRotation>()
    private val rotationJobs = mutableMapOf<String, Job>()
    private val rotationBases = mutableMapOf<String, Int>()
    private val appliedRotations = mutableMapOf<String, EmulatorRotation>()
    fun rotateCurrent(clockwise: Boolean): Boolean {
        val device = previewDevice ?: return false
        val serial = running[device.name] ?: return false
        val previous = rotations[device.name]?.takeIf { it.serial == serial }
        if (previous == null) rotationJobs.remove(device.name)?.cancel()
        // Publish before doing any IO so every press immediately retargets the preview.
        rotations[device.name] = EmulatorRotation(
            turns = (previous?.turns ?: 0) + if (clockwise) 1 else -1,
            initialRotation = previous?.initialRotation ?: frameRotations[device.directory.absolutePath],
            serial = serial,
        )
        if (rotationJobs[device.name]?.isActive == true) return true
        val backend = service
        rotationJobs[device.name] = scope.launch {
            try {
                val base = rotationBases[serial] ?: (
                    rotations[device.name]?.initialRotation ?: withContext(Dispatchers.IO) {
                        backend.displayRotation(serial)
                    }
                ).also { rotationBases[serial] = it }
                while (running[device.name] == serial) {
                    val latest = rotations[device.name]?.takeIf { it.serial == serial } ?: break
                    val applied = appliedRotations[device.name]?.takeIf { it.serial == serial }
                    if (latest.turns == (applied?.turns ?: 0)) break
                    val target = Math.floorMod(base - latest.turns, 4)
                    withContext(Dispatchers.IO) {
                        display.rotate(serial, target)
                    }
                    appliedRotations[device.name] = latest
                    // Re-read the latest target instead of queuing intermediate keypresses.
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                if (rotations[device.name]?.serial == serial) {
                    val applied = appliedRotations[device.name]?.takeIf { it.serial == serial }
                    if (applied == null) rotations.remove(device.name) else rotations[device.name] = applied
                }
                report(e)
            }
        }
        return true
    }
    fun sendTouch(device: Device, x: Int, y: Int, pressed: Boolean) {
        val serial = running[device.name] ?: return
        scope.launch {
            try { inputMutex.withLock { withContext(Dispatchers.IO) { display.touch(serial, x, y, pressed) } } }
            catch (e: Exception) {
                if (e is CancellationException) throw e
                displayErrors[device.name] = "Could not send touch input. Try restarting the device."
            }
        }
    }
    fun sendInput(device: Device, arguments: List<String>) {
        val serial = running[device.name] ?: return
        val current = settings
        scope.launch {
            try { inputMutex.withLock { withContext(Dispatchers.IO) { display.input(current, serial, arguments) } } }
            catch (e: Exception) { if (e is CancellationException) throw e; report(e) }
        }
    }
    var images by mutableStateOf(emptyList<SystemImage>()); private set
    var profiles by mutableStateOf(emptyList<DeviceProfile>()); private set
    var busy by mutableStateOf(false); private set
    var status by mutableStateOf("")
    var error by mutableStateOf(false); private set
    var catalogLoading by mutableStateOf(false); private set
    var catalogError by mutableStateOf(""); private set
    var showSettings by mutableStateOf(false)
    var showCreate by mutableStateOf(false)
    var showAbout by mutableStateOf(false)
    var renameDevice by mutableStateOf<Device?>(null)
    var deleteDevices by mutableStateOf(emptyList<Device>())
    var creating by mutableStateOf(false); private set
    var createCancelling by mutableStateOf(false); private set
    var createFinished by mutableStateOf(false); private set
    var createError by mutableStateOf(""); private set
    val output = MutableStateFlow("")
    private var cancellation = AtomicBoolean()
    private var catalogJob: Job? = null
    val service get() = AndroidService(settings, executor)
    val ready get() = service.toolchain.ready
    val selectedDevices get() = devices.filter { it.name in selected }
    val totalBytes get() = devices.sumOf(Device::bytes)

    suspend fun refresh() {
        val backend = service
        val list = withContext(Dispatchers.IO) { backend.devices() }
        if (settings == backend.settings) { devices = list; selected = selected.intersect(list.map { it.name }.toSet()) }
        val active = withContext(Dispatchers.IO) { runCatching { backend.running() }.getOrDefault(emptyMap()) }
        if (settings == backend.settings) {
            running.filter { (name, serial) -> active[name] != serial }.forEach { (name, serial) ->
                rotationJobs.remove(name)?.cancel()
                rotations.remove(name)
                appliedRotations.remove(name)
                rotationBases.remove(serial)
            }
            val stopped = running.keys - active.keys
            for (device in devices.filter { it.name in stopped }) {
                val image = withContext(Dispatchers.IO) { display.load(device)?.toComposeImageBitmap() }
                if (image != null) frames[device.directory.absolutePath] = image
            }
            running = active
        }
    }
    fun refreshAsync() = scope.launch { runCatching { refresh() }.onFailure { report(it) } }
    private fun report(t: Throwable) { status = t.message ?: t.toString(); error = true }
    private fun action(success: String, onResult: (String?) -> Unit = {}, block: (AndroidService) -> Unit) {
        if (busy) return
        val backend = service
        scope.launch {
            busy = true; error = false
            try { withContext(Dispatchers.IO) { block(backend) }; status = success; refresh(); onResult(null) }
            catch (t: Exception) { if (t is CancellationException) throw t; report(t); onResult(t.message ?: "The operation could not be completed.") }
            finally { busy = false }
        }
    }
    fun launch(device: Device) {
        if (busy || device.name in running || device.name in starting) return
        selectPreview(device)
        starting = starting + device.name
        val backend = service
        scope.launch {
            busy = true
            try {
                withContext(Dispatchers.IO) { backend.launch(device) }
                status = "Starting ${device.displayName}…"; error = false
            } catch (e: Exception) {
                starting = starting - device.name
                if (e is CancellationException) throw e
                report(e); return@launch
            } finally { busy = false }
            try {
                val started = withTimeoutOrNull(90_000) {
                    while (device.name !in running) { refresh(); delay(1000) }
                    true
                } ?: false
                if (!started) { status = "${device.displayName} did not start. Try again or check the Android SDK."; error = true }
                else status = "${device.displayName} is running."
            } finally { starting = starting - device.name }
        }
    }
    fun stop(device: Device) = action("Stopped ${device.displayName}.") { backend ->
        running[device.name]?.let { serial ->
            runCatching { display.capture(backend.settings, device, serial) }
            backend.stop(serial)
        }
    }
    fun restart(device: Device) = action("Restarting ${device.displayName}…") { backend ->
        running[device.name]?.let(backend::restart)
    }
    fun stopAll() = action("Stopped all running emulators.") { backend -> running.forEach { (name, serial) ->
        devices.firstOrNull { it.name == name }?.let { runCatching { display.capture(backend.settings, it, serial) } }; backend.stop(serial) } }
    fun duplicate(device: Device) = action("Duplicated ${device.displayName}.") {
        require(device.name !in running && device.name !in starting) { "Stop ${device.displayName} before duplicating it." }
        it.duplicate(device)
    }
    fun rename(device: Device, name: String, onResult: (String?) -> Unit = {}) = action("Renamed device to $name.", onResult) {
        require(device.name !in running && device.name !in starting) { "Stop ${device.displayName} before renaming it." }; it.rename(device, name.trim())
    }
    fun delete(devices: List<Device>) = action("Deleted ${devices.size} device${if (devices.size == 1) "" else "s"}.") { backend ->
        devices.forEach { device -> running[device.name]?.let(backend::stop); backend.delete(device) }
    }
    fun reveal(device: Device) = action("Opened ${device.displayName} in Finder.") {
        val file = device.directory.takeIf(File::exists) ?: File(it.avdRoot, "${device.name}.ini")
        require(file.exists()) { "The device's storage is unavailable." }
        Desktop.getDesktop().browseFileDirectory(file)
    }
    fun saveSettings(sdk: String, avds: String): String? {
        if (busy) return "Wait for the current operation to finish."
        if (sdk.isBlank() || avds.isBlank()) return "Choose both an SDK folder and an emulator storage folder."
        val sdkFolder = expandPath(sdk); val avdFolder = expandPath(avds)
        if (!Toolchain(sdkFolder).ready) return "Select an SDK containing command-line tools, emulator, and platform-tools."
        if (!avdFolder.isDirectory || !avdFolder.canWrite()) return "Choose an available, writable emulator folder."
        val updated = AppSettings(sdkFolder.path, avdFolder.path)
        return runCatching {
            store.save(updated); settings = updated; selected = emptySet(); images = emptyList(); profiles = emptyList()
            catalogJob?.cancel(); catalogLoading = false; showSettings = false; status = "Settings saved."; error = false
            refreshAsync(); null
        }.getOrElse { it.message ?: "Could not save settings." }
    }
    fun loadCatalog() {
        if (catalogLoading || images.isNotEmpty()) return
        val backend = service
        catalogJob = scope.launch {
            catalogLoading = true; catalogError = ""
            try {
                backend.toolchain.requireReady()
                val result = withContext(Dispatchers.IO) { backend.images() to backend.profiles() }
                if (settings == backend.settings) { images = result.first; profiles = result.second }
            } catch (t: Exception) { if (t !is CancellationException) catalogError = t.message ?: "Could not load Android versions." }
            finally { catalogLoading = false }
        }
    }
    suspend fun reviewStorage(spec: CreateSpec): StoragePlan = withContext(Dispatchers.IO) { storagePlanner(spec, settings) }
    fun create(spec: CreateSpec) {
        if (busy) return
        cancellation = AtomicBoolean(); val token = cancellation; val backend = service
        output.value = ""; createError = ""; createFinished = false; createCancelling = false; creating = true; busy = true
        scope.launch {
            try {
                withContext(Dispatchers.IO) {
                    val plan = storagePlanner(spec, backend.settings)
                    check(plan.canCreate) { "Not enough available disk space to create this device. Free space and review the storage requirements, then retry." }
                    backend.validateName(spec.name)
                    backend.create(spec, token) { chunk -> output.update { (it + chunk.replace("\r\n", "\n").replace('\r', '\n')).takeLast(80_000) } } }
                createFinished = true; status = "Created ${spec.name}."; error = false
                refresh(); selected = setOf(spec.name); previewName = spec.name
                images = images.map { if (it.path == spec.image.path) it.copy(installed = true) else it }
            } catch (t: Exception) { createError = if (token.get()) "Creation cancelled." else t.message ?: "Creation failed." }
            finally { creating = false; createCancelling = false; busy = false }
        }
    }
    fun cancelCreate() { if (creating) { createCancelling = true; cancellation.set(true) } }
    fun resetCreate() { createCancelling = false; createFinished = false; createError = ""; output.value = ""; showCreate = true; loadCatalog() }
    fun copyDiagnostics() {
        val text = service.diagnostics()
        Toolkit.getDefaultToolkit().systemClipboard.setContents(StringSelection(text), null)
        status = "Diagnostics copied."; error = false
    }
    val updates = AppUpdates(scope)
    fun checkUpdates() = updates.check()
}
