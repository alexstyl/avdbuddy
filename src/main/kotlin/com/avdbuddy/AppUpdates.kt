package com.avdbuddy

import androidx.compose.runtime.*
import dev.nucleusframework.updater.*
import dev.nucleusframework.updater.provider.GenericProvider
import kotlinx.coroutines.*
import kotlinx.serialization.json.*
import java.io.File
import java.util.Properties

internal fun appBundle(javaHome: File = File(System.getProperty("java.home"))): File? =
    generateSequence(javaHome) { it.parentFile }.firstOrNull { it.extension == "app" }

internal fun installedForUpdates(os: HostOs = HostOs.detect(), bundle: File? = appBundle(),
                                 appImage: String? = System.getenv("APPIMAGE"),
                                 executable: String? = System.getProperty("jpackage.app-path")): Boolean = when (os) {
    HostOs.MAC -> bundle?.let { generateSequence(it) { it.parentFile }.none { f -> f.name == "build" } } == true
    HostOs.LINUX -> !appImage.isNullOrBlank()
    HostOs.WINDOWS -> executable?.let { File(it).extension.equals("exe", true) } == true
}

internal fun updatePlatform(os: HostOs = HostOs.detect(), arch: String = System.getProperty("os.arch")): String? {
    val cpu = when (arch.lowercase()) { "aarch64", "arm64" -> "arm64"; "x86_64", "amd64", "x64" -> "x64"; else -> return null }
    return when (os) { HostOs.MAC -> "macos-$cpu"; HostOs.WINDOWS -> "windows-$cpu"; HostOs.LINUX -> "linux-$cpu" }
        .takeIf { it in setOf("macos-arm64", "macos-x64", "windows-x64", "linux-x64") }
}

class AppUpdates(private val scope: CoroutineScope, private val license: AppLicense) {
    private var updater: NucleusUpdater? = null
    private val preference = File(HostPlatform().configDirectory, "updates.properties")
    var automatic by mutableStateOf(runCatching { Properties().apply { preference.inputStream().use(::load) }.getProperty("automatic", "true").toBoolean() }.getOrDefault(true)); private set
    var visible by mutableStateOf(false)
    var working by mutableStateOf(false); private set
    var message by mutableStateOf(""); private set
    var available by mutableStateOf<UpdateInfo?>(null); private set
    var downloaded by mutableStateOf<File?>(null); private set
    val unavailableReason = if (installedForUpdates() && updatePlatform() != null) null else "Updates are available in installed builds of AvdBuddy."

    fun changeAutomatic(value: Boolean) {
        runCatching {
            preference.parentFile.mkdirs()
            Properties().apply { setProperty("automatic", value.toString()) }.let { props -> preference.outputStream().use { props.store(it, null) } }
            automatic = value
        }.onFailure { message = "Could not save update preferences."; visible = true }
    }
    suspend fun monitor() {
        if (unavailableReason != null) return
        while (currentCoroutineContext().isActive) {
            if (automatic) check(manual = false)
            delay(12 * 60 * 60 * 1000L)
        }
    }
    fun check(manual: Boolean = true) {
        if (manual) visible = true
        if (working || downloaded != null) return
        unavailableReason?.let { message = it; return }
        working = true; message = "Checking for updates…"
        scope.launch {
            try {
                val receipt = license.receipt
                val response = licenseRequest("/api/v1/updates", buildJsonObject {
                    put("product", "avdbuddy"); put("platform", updatePlatform()!!)
                    receipt?.let { put("receipt", it) }
                })
                val release = response["release"]?.takeUnless { it is JsonNull }?.jsonObject
                if (release == null) { available = null; message = "No updates are available for this installation yet."; return@launch }
                val baseUrl = release.getValue("base_url").jsonPrimitive.content
                require(baseUrl.startsWith("https://downloads.avdbuddy.com/releases/"))
                val updater = NucleusUpdater {
                    currentVersion = APP_VERSION
                    provider = GenericProvider(baseUrl)
                    executableType = when (HostOs.detect()) { HostOs.MAC -> "zip"; HostOs.WINDOWS -> "exe"; HostOs.LINUX -> "AppImage" }
                    differentialDownload = false
                }
                this@AppUpdates.updater = updater
                when (val result = updater.checkForUpdates()) {
                    is UpdateResult.Available -> { available = result.info; message = "AvdBuddy ${result.info.version} is available."; visible = true }
                    is UpdateResult.NotAvailable -> { available = null; message = "You're up to date. AvdBuddy $APP_VERSION is installed." }
                    is UpdateResult.Error -> { available = null; message = "Could not check for updates. Please try again later." }
                }
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { available = null; message = "Could not check for updates. Please try again later." }
            finally { working = false }
        }
    }
    fun download() {
        if (unavailableReason != null) return
        val updater = updater ?: return
        val info = available ?: return
        if (working) return
        working = true
        scope.launch {
            try {
                updater.downloadUpdate(info).collect { progress ->
                    message = "Downloading AvdBuddy ${info.version} — ${progress.percent.toInt()}%"
                    progress.file?.let { downloaded = it; message = "Update ready. Restart AvdBuddy to install it." }
                }
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { message = "The update could not be downloaded or verified. Please try again." }
            finally { working = false }
        }
    }
    fun restart(busy: Boolean) {
        if (busy || working || unavailableReason != null) return
        val updater = updater ?: return
        val file = downloaded ?: return
        working = true
        scope.launch {
            try { updater.installAndRestart(file) }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) { message = "Could not install the update. Please try again." }
            finally { working = false }
        }
    }
}
