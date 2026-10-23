package com.avdbuddy

import java.util.Properties

const val APP_NAME = "AvdBuddy"
val APP_VERSION: String by lazy {
    val properties = Properties().apply {
        checkNotNull(object {}.javaClass.getResourceAsStream("/avdbuddy-build.properties")) {
            "Missing app version."
        }.use(::load)
    }
    checkNotNull(properties.getProperty("version")) { "Missing app version." }
}
