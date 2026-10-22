package com.avdbuddy

import java.io.File
import java.io.InputStream
import org.yaml.snakeyaml.LoaderOptions
import org.yaml.snakeyaml.Yaml
import org.yaml.snakeyaml.constructor.SafeConstructor

data class ProfileDisplay(val width: Int, val height: Int, val density: Int)
data class ProfileFrame(val directory: String, val width: Int, val height: Int, val screenX: Int, val screenY: Int, val cornerRadius: Int)
data class HardwareProfile(
    val id: String, val name: String, val type: DeviceType, val display: ProfileDisplay,
    val ramMiB: Int, val storageGiB: Int, val frame: ProfileFrame,
) {
    fun createSpec(name: String, image: SystemImage, existingNames: Collection<String>): CreateSpec {
        require(image in googlePlayImages(listOf(image))) { "Choose a Google Play image compatible with this computer." }
        return CreateSpec(availableAvdName(name, existingNames), image, id, ramMiB, storageGiB,
            hardware = this, displayName = name.trim())
    }

    fun installFrame(directory: File): File = File(directory, "avdbuddy-skin").apply {
        check(isDirectory || mkdirs()) { "Could not prepare the device frame." }
        for (name in listOf("layout", "back.webp", "mask.webp")) {
            outputStreamFor(name).use { output -> profileResource("${frame.directory}/$name").use { it.copyTo(output) } }
        }
    }

    private fun File.outputStreamFor(name: String) = File(this, name).outputStream()
}

fun profileResource(path: String): InputStream = checkNotNull(HardwareProfile::class.java.getResourceAsStream("/$path")) {
    "Missing bundled profile resource: $path"
}

object HardwareProfiles {
    val all: List<HardwareProfile> by lazy {
        listOf("pixel-10").map { id -> profileResource("profiles/$id.yml").use { parseHardwareProfile(it) } }
            .also { profiles ->
                require(profiles.map { it.id }.distinct().size == profiles.size) { "Duplicate device profile IDs." }
                profiles.forEach { profile ->
                    for (name in listOf("layout", "back.webp", "mask.webp")) profileResource("${profile.frame.directory}/$name").close()
                }
            }
    }
}

fun parseHardwareProfile(input: InputStream): HardwareProfile {
    val options = LoaderOptions().apply { isAllowDuplicateKeys = false; maxAliasesForCollections = 0; codePointLimit = 64 * 1024 }
    val root = Yaml(SafeConstructor(options)).load<Map<String, Any>>(input)
    fun Map<*, *>.text(key: String) = (get(key) as? String)?.takeIf(String::isNotBlank) ?: error("Missing profile field: $key")
    fun Map<*, *>.number(key: String, min: Int = 1, max: Int = 16384): Int {
        val value = get(key) as? Int ?: error("Invalid profile number: $key")
        require(value in min..max) { "Profile field $key is out of range." }
        return value
    }
    fun section(key: String) = root[key] as? Map<*, *> ?: error("Missing profile section: $key")
    require(root.number("schema") == 1) { "Unsupported profile schema." }
    val id = root.text("id")
    require(Regex("[a-z0-9]+(?:-[a-z0-9]+)*").matches(id)) { "Invalid profile ID." }
    val display = section("display").let { ProfileDisplay(it.number("width"), it.number("height"), it.number("density", max = 1000)) }
    val frame = section("frame").let {
        val directory = it.text("directory")
        require(Regex("device-frames/[a-z0-9-]+").matches(directory)) { "Invalid frame directory." }
        ProfileFrame(directory, it.number("width"), it.number("height"), it.number("screenX", min = 0), it.number("screenY", min = 0), it.number("cornerRadius", min = 0))
    }
    require(frame.screenX + display.width <= frame.width && frame.screenY + display.height <= frame.height) { "The screen does not fit its frame." }
    require(frame.cornerRadius <= minOf(display.width, display.height) / 2) { "Invalid screen corner radius." }
    val emulator = section("emulator")
    val type = DeviceType.entries.firstOrNull { it.name.equals(root.text("type"), true) } ?: error("Unknown device type.")
    return HardwareProfile(id, root.text("name"), type, display, emulator.number("ramMiB", 512, 32768), emulator.number("storageGiB", 1, 1024), frame)
}

fun nativeImageAbi(architecture: String = System.getProperty("os.arch")): String? = when (architecture.lowercase()) {
    "aarch64", "arm64" -> "arm64-v8a"
    "x86_64", "amd64" -> "x86_64"
    else -> null
}

/** Stable Google Play releases only; never substitute a non-native or Google-APIs-only image. */
fun googlePlayImages(images: List<SystemImage>, architecture: String = System.getProperty("os.arch")): List<SystemImage> {
    val abi = nativeImageAbi(architecture) ?: return emptyList()
    return images.filter { it.abi == abi && it.tag == "google_apis_playstore" && Regex("\\d+(?:\\.\\d+)*(?:-ext\\d+)?").matches(it.api) }
        .sortedWith(compareBy<SystemImage> { "-ext" in it.api }.thenByDescending { it.installed }.thenBy { it.path })
        .distinctBy { it.api.substringBefore("-ext").removeSuffix(".0") }
        .sortedWith(compareByDescending(androidReleaseOrder) { it.api })
}

fun creationApiLabel(image: SystemImage): String = image.api.substringBefore("-ext").removeSuffix(".0")

fun availableAvdName(displayName: String, existing: Collection<String>): String {
    val candidate = displayName.trim().replace(Regex("[^A-Za-z0-9._-]+"), "_").trim('_').take(80).ifBlank { "Pixel" }
    val base = if (isReservedDeviceName(candidate)) "_$candidate" else candidate
    var name = base
    var suffix = 2
    while (existing.any { it.equals(name, true) }) name = "${base}_${suffix++}"
    return name
}

fun suggestedDeviceName(base: String, existing: Collection<String>): String {
    var name = base
    var suffix = 2
    while (existing.any { it.equals(name, true) }) name = "$base ${suffix++}"
    return name
}

fun creationNameError(name: String, existing: Collection<String> = emptyList()): String? = when {
    name.isBlank() -> "Enter a name for your device."
    name.trim().length > 100 -> "Use a name with 100 characters or fewer."
    name.any(Char::isISOControl) -> "Use a name without control characters."
    existing.any { it.equals(name.trim(), true) } -> "A device with this name already exists. Choose another name."
    else -> null
}
