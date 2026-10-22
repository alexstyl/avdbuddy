package com.avdbuddy

fun deviceNameError(name: String, existingNames: Collection<String>): String? {
    val value = name.trim()
    return when {
        value.isEmpty() -> "Enter a device name."
        !Regex("[A-Za-z0-9._-]+").matches(value) -> "Use letters, numbers, dots, underscores or hyphens. Spaces are not supported."
        isReservedDeviceName(value) -> "This name is reserved by Windows. Choose a different name."
        existingNames.any { it.equals(value, ignoreCase = true) } -> "A device named $value already exists. Choose a different name."
        else -> null
    }
}

// Windows reserves these stems even when a suffix such as .avd or .ini is added.
// Keep identifiers portable so devices can be moved between operating systems.
internal fun isReservedDeviceName(name: String): Boolean =
    Regex("(?i)(CON|PRN|AUX|NUL|COM[1-9]|LPT[1-9])").matches(name.substringBefore('.'))
