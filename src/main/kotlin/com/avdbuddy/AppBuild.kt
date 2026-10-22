package com.avdbuddy

import java.util.Properties

internal data class BuildIdentity(val channel: String, val version: String) {
    val isNightly get() = channel == "nightly"
    val appName get() = if (isNightly) "AvdBuddy Nightly" else "AvdBuddy"
    val settingsDirectoryName get() = if (isNightly) "AvdBuddy-Nightly" else "AvdBuddy"
}

internal val appBuild: BuildIdentity by lazy {
    val properties = Properties().apply {
        checkNotNull(BuildIdentity::class.java.getResourceAsStream("/avdbuddy-build.properties")) {
            "Missing build identity."
        }.use(::load)
    }
    BuildIdentity(properties.getProperty("channel"), properties.getProperty("version"))
}
val APP_VERSION: String get() = appBuild.version
val APP_NAME: String get() = appBuild.appName
