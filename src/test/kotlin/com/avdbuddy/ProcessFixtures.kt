package com.avdbuddy

import java.io.File

internal fun fixtureCommand(vararg args: String): Command = Command(
    File(System.getProperty("java.home"), "bin/${HostPlatform().executable("java")}"),
    listOf("-cp", File(FixtureProcess::class.java.protectionDomain.codeSource.location.toURI()).path,
        FixtureProcess::class.java.name) + args,
)
