package com.avdbuddy

import androidx.compose.runtime.*
import dev.nucleusframework.updater.*
import kotlinx.coroutines.*
import java.io.File
import java.util.Properties

internal fun appBundle(javaHome: File = File(System.getProperty("java.home"))): File? =
    generateSequence(javaHome) { it.parentFile }.firstOrNull { it.extension == "app" }

internal fun updateInstallation(bundle: File?, updatesConfigured: Boolean): String? = when {
    !updatesConfigured -> "Automatic updates are not available yet."
    bundle == null || generateSequence(bundle) { it.parentFile }.any { it.name == "build" } -> "Updates are available in installed builds of AvdBuddy."
    else -> null
}

class AppUpdates(private val scope: CoroutineScope, private val updater: NucleusUpdater? = null) {
    private val preference = File(HostPlatform().configDirectory, "updates.properties")
    var automatic by mutableStateOf(runCatching { Properties().apply { preference.inputStream().use(::load) }.getProperty("automatic", "true").toBoolean() }.getOrDefault(true)); private set
    var visible by mutableStateOf(false)
    var working by mutableStateOf(false); private set
    var message by mutableStateOf(""); private set
    var available by mutableStateOf<UpdateInfo?>(null); private set
    var downloaded by mutableStateOf<File?>(null); private set
    val unavailableReason = updateInstallation(appBundle(), updatesConfigured = updater != null)

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
        val updater = updater ?: return
        working = true; message = "Checking for updates…"
        scope.launch {
            try {
                when (val result = updater.checkForUpdates()) {
                    is UpdateResult.Available -> { available = result.info; message = "AvdBuddy ${result.info.version} is available."; visible = true }
                    is UpdateResult.NotAvailable -> { available = null; message = "You're up to date. AvdBuddy $APP_VERSION is installed." }
                    is UpdateResult.Error -> { available = null; message = "Could not check for updates. Please try again later." }
                }
            } finally { working = false }
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
