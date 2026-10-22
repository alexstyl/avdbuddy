package com.avdbuddy

import androidx.compose.runtime.*
import androidx.compose.ui.graphics.toComposeImageBitmap
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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
    var pendingCreation by mutableStateOf<Device?>(null); private set
    val visibleDevices get() = pendingCreation?.let { pending ->
        devices.filterNot { it.name == pending.name || it.displayName == pending.displayName } + pending
    } ?: devices
    val previewDevice get() = visibleDevices.firstOrNull { it.name == previewName } ?: selectedDevices.firstOrNull() ?: visibleDevices.firstOrNull()
    val frames = mutableStateMapOf<String, androidx.compose.ui.graphics.ImageBitmap>()
    val frameRotations = mutableStateMapOf<String, Int>()
    val displayErrors = mutableStateMapOf<String, String>()
    var starting by mutableStateOf(setOf<String>()); private set
    val deviceOperations = mutableStateMapOf<String, String>()
    private var selectionAnchor: String? = null
    fun selectPreview(device: Device) = selectDevice(device)
    fun selectDevice(device: Device, toggle: Boolean = false, extend: Boolean = false,
                     orderedNames: List<String> = devices.map { it.name }) {
        val anchor = orderedNames.indexOf(selectionAnchor)
        val target = orderedNames.indexOf(device.name)
        selected = when {
            extend && anchor >= 0 && target >= 0 -> {
                val range = orderedNames.subList(minOf(anchor, target), maxOf(anchor, target) + 1).toSet()
                if (toggle) selected + range else range
            }
            toggle -> if (device.name in selected) selected - device.name else selected + device.name
            else -> setOf(device.name)
        }
        if (!extend || anchor < 0) selectionAnchor = device.name
        previewName = if (device.name in selected) device.name else orderedNames.firstOrNull { it in selected }
    }
    fun selectForContextMenu(device: Device) {
        if (device.name !in selected) selectDevice(device)
    }
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
    var creationSpec by mutableStateOf<CreateSpec?>(null); private set
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
        if (settings == backend.settings) { devices = list; selected = selected.intersect(visibleDevices.map { it.name }.toSet()) }
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
    private fun action(success: String, onResult: (String?) -> Unit = {}, operations: Map<String, String> = emptyMap(), block: (AndroidService) -> Unit) {
        if (busy) return
        val backend = service
        deviceOperations.putAll(operations)
        busy = true; error = false
        scope.launch {
            try { withContext(Dispatchers.IO) { block(backend) }; status = success; refresh(); onResult(null) }
            catch (t: Exception) { if (t is CancellationException) throw t; runCatching { refresh() }; report(t); onResult(t.message ?: "The operation could not be completed.") }
            finally { operations.keys.forEach(deviceOperations::remove); busy = false }
        }
    }
    fun launch(device: Device) {
        if (busy || device.name in running || device.name in starting) return
        selectPreview(device)
        launch(listOf(device))
    }
    fun launch(devices: List<Device>) {
        if (busy) return
        val targets = devices.distinctBy { it.name }.filter {
            it.name !in running && it.name !in starting && !it.missingStorage
        }
        if (targets.isEmpty()) return
        val names = targets.map { it.name }.toSet()
        starting = starting + names
        busy = true; error = false
        val backend = service
        scope.launch {
            val failures = mutableListOf<String>()
            val launched = mutableSetOf<String>()
            try {
                for (device in targets) {
                    try {
                        withContext(Dispatchers.IO) { backend.launch(device) }
                        launched += device.name
                    } catch (e: Exception) {
                        if (e is CancellationException) throw e
                        failures += "${device.displayName}: ${e.message}"
                        starting = starting - device.name
                    }
                }
                status = "Starting ${launched.size} device${if (launched.size == 1) "" else "s"}…"
            } finally { busy = false }
            try {
                val started = withTimeoutOrNull(90_000) {
                    while (!running.keys.containsAll(launched)) { refresh(); delay(1000) }
                    true
                } ?: false
                if (!started) failures += "Some devices did not start. Try again or check the Android SDK."
                if (failures.isEmpty()) status = "${launched.size} device${if (launched.size == 1) " is" else "s are"} running."
                else report(IllegalStateException(failures.joinToString("\n")))
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                report(e)
            } finally { starting = starting - names }
        }
    }
    private fun forEachDevice(devices: List<Device>, block: (Device) -> Unit) {
        val failures = mutableListOf<String>()
        devices.distinctBy { it.name }.forEach { device ->
            try { block(device) }
            catch (e: Exception) {
                if (e is CancellationException) throw e
                failures += "${device.displayName}: ${e.message}"
            }
        }
        check(failures.isEmpty()) { failures.joinToString("\n") }
    }
    fun stop(device: Device) = stop(listOf(device))
    fun stop(devices: List<Device>) {
        val targets = devices.filter { it.name in running && it.name !in starting }
        val serials = running.toMap()
        action("Stopped ${targets.size} device${if (targets.size == 1) "" else "s"}.", operations = targets.associate { it.name to "Shutting down…" }) { backend ->
            forEachDevice(targets) { device -> serials[device.name]?.let { serial ->
                runCatching { display.capture(backend.settings, device, serial) }
                backend.stop(serial)
            } }
        }
    }
    fun restart(device: Device) = restart(listOf(device))
    fun restart(devices: List<Device>) {
        val targets = devices.filter { it.name in running && it.name !in starting }
        val serials = running.toMap()
        action("Restarting ${targets.size} device${if (targets.size == 1) "" else "s"}…", operations = targets.associate { it.name to "Restarting…" }) { backend ->
            forEachDevice(targets) { device -> serials[device.name]?.let(backend::restart) }
        }
    }
    fun stopAll() = action("Stopped all running emulators.", operations = running.keys.associateWith { "Shutting down…" }) { backend -> running.forEach { (name, serial) ->
        devices.firstOrNull { it.name == name }?.let { runCatching { display.capture(backend.settings, it, serial) } }; backend.stop(serial) } }
    fun duplicate(device: Device) = action("Duplicated ${device.displayName}.") {
        require(device.name !in running && device.name !in starting) { "Stop ${device.displayName} before duplicating it." }
        it.duplicate(device)
    }
    fun rename(device: Device, name: String, onResult: (String?) -> Unit = {}) = action("Renamed device to $name.", onResult) {
        require(device.name !in running && device.name !in starting) { "Stop ${device.displayName} before renaming it." }; it.rename(device, name.trim())
    }
    fun delete(devices: List<Device>) = action("Deleted ${devices.size} device${if (devices.size == 1) "" else "s"}.") { backend ->
        forEachDevice(devices) { device ->
            require(device.name !in starting) { "Wait for this device to finish starting before removing it." }
            running[device.name]?.let(backend::stop); backend.delete(device)
        }
    }
    fun reveal(device: Device) = reveal(listOf(device))
    fun reveal(devices: List<Device>) = action("Opened ${devices.size} device${if (devices.size == 1) "" else "s"} in ${HostPlatform().fileManagerName}.") { backend ->
        forEachDevice(devices) { device ->
            val file = device.directory.takeIf(File::exists) ?: File(backend.avdRoot, "${device.name}.ini")
            require(file.exists()) { "The device's storage is unavailable." }
            revealInFileManager(file)
        }
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
    fun loadCatalog(force: Boolean = false) {
        if (catalogLoading || (!force && images.isNotEmpty())) return
        val backend = service
        catalogJob = scope.launch {
            catalogLoading = true; catalogError = ""
            try {
                backend.toolchain.requireReady()
                val result = withContext(Dispatchers.IO) { backend.images() }
                if (settings == backend.settings) images = result
            } catch (t: Exception) { if (t !is CancellationException) catalogError = t.message ?: "Could not load Android versions." }
            finally { catalogLoading = false }
        }
    }
    suspend fun reviewStorage(spec: CreateSpec): StoragePlan = withContext(Dispatchers.IO) { storagePlanner(spec, settings) }
    fun create(spec: CreateSpec) {
        if (busy) return
        val resumeCreated = createFinished && pendingCreation != null && creationSpec == spec
        creationSpec = spec
        cancellation = AtomicBoolean(); val token = cancellation; val backend = service
        pendingCreation = Device(spec.name, File(backend.avdRoot, "${spec.name}.avd"), buildMap {
            put("avd.ini.displayname", spec.displayName)
            put("hw.device.name", spec.profile.replace('-', '_'))
            put("image.sysdir.1", spec.image.path.replace(';', '/') + "/")
            spec.hardware?.let { profile ->
                put("avdbuddy.profile", profile.id)
                put("showDeviceFrame", "yes")
                put("hw.lcd.width", profile.display.width.toString())
                put("hw.lcd.height", profile.display.height.toString())
                put("hw.lcd.density", profile.display.density.toString())
            }
        }, 0)
        selectPreview(pendingCreation!!)
        showCreate = false
        output.value = ""; createError = ""; createFinished = false; createCancelling = false; creating = true; busy = true
        scope.launch {
            var startWhenReady: Device? = null
            try {
                val created = if (resumeCreated) spec else withContext(Dispatchers.IO) {
                    val plan = storagePlanner(spec, backend.settings)
                    check(plan.canCreate) { "Not enough available disk space to create this device. Free space and review the storage requirements, then retry." }
                    backend.create(spec, token) { chunk -> output.update { (it + chunk.replace("\r\n", "\n").replace('\r', '\n')).takeLast(80_000) } } }
                creationSpec = created
                pendingCreation = pendingCreation?.copy(name = created.name, directory = File(backend.avdRoot, "${created.name}.avd"))
                pendingCreation?.let(::selectPreview)
                createFinished = true; status = "Created ${created.displayName}."; error = false
                refresh(); selected = setOf(created.name); previewName = created.name
                images = images.map { if (it.path == spec.image.path) it.copy(installed = true) else it }
                val device = devices.firstOrNull { it.name == created.name } ?: error("Your device was created but could not be loaded. Try again.")
                pendingCreation = null
                if (!token.get()) startWhenReady = device
            } catch (t: Exception) {
                if (token.get()) {
                    discardPendingCreation()
                    refreshAsync()
                } else createError = t.message ?: "Could not set up your device. Try again."
            }
            finally { creating = false; createCancelling = false; busy = false }
            startWhenReady?.let(::launch)
        }
    }
    fun cancelCreate() { if (creating) { createCancelling = true; cancellation.set(true) } }
    fun resetCreate() { if (!busy) { showCreate = true; loadCatalog() } }

    fun retryCreation() { if (!busy) creationSpec?.let(::create) }
    fun discardPendingCreation() {
        pendingCreation?.let { pending ->
            selected = selected - pending.name
            if (previewName == pending.name) previewName = devices.firstOrNull()?.name
        }
        pendingCreation = null; creationSpec = null; createError = ""; createFinished = false
    }
    fun copyDiagnostics() {
        val text = service.diagnostics()
        Toolkit.getDefaultToolkit().systemClipboard.setContents(StringSelection(text), null)
        status = "Diagnostics copied."; error = false
    }
    val updates = AppUpdates(scope)
    fun checkUpdates() = updates.check()
}
