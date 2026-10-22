package com.avdbuddy

fun deviceNameError(name: String, existingNames: Collection<String>): String? {
    val value = name.trim()
    return when {
        value.isEmpty() -> "Enter a device name."
        !Regex("[A-Za-z0-9._-]+").matches(value) -> "Use letters, numbers, dots, underscores or hyphens. Spaces are not supported."
        existingNames.any { it.equals(value, ignoreCase = true) } -> "A device named $value already exists. Choose a different name."
        else -> null
    }
}
