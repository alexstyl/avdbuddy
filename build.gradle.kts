import dev.nucleusframework.desktop.application.dsl.TargetFormat
plugins {
    kotlin("jvm") version "2.4.0"
    kotlin("plugin.compose") version "2.4.0"
    id("org.jetbrains.compose") version "1.12.0"
    id("org.jetbrains.compose.hot-reload") version "1.2.0"
    id("dev.nucleusframework") version "2.5.18"
    id("com.composables.kpm") version "0.2.0"
    id("com.google.protobuf") version "0.10.0"
}
protobuf { protoc { artifact = "com.google.protobuf:protoc:3.25.9" } }
kotlin { jvmToolchain { languageVersion = JavaLanguageVersion.of(25); vendor = JvmVendorSpec.JETBRAINS } }

dependencies {
    implementation(compose.desktop.currentOs) {
        // Exclude Material from this app's UI, not from Hot Reload's separate developer tools.
        exclude(group = "org.jetbrains.compose.material")
        exclude(group = "org.jetbrains.compose.material3")
        exclude(group = "androidx.compose.material")
        exclude(group = "androidx.compose.material3")
    }
    testImplementation(kotlin("test"))
    testImplementation("org.junit.jupiter:junit-jupiter:5.14.4")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}
tasks.test {
    useJUnitPlatform()
    maxParallelForks = 1
    systemProperty("java.awt.headless", "true")
    jvmArgs("--enable-native-access=ALL-UNNAMED")
    testLogging { events("passed", "failed", "skipped"); showStandardStreams = true }
}
nucleus {
    application {
        mainClass = "com.avdbuddy.MainKt"
        javaHome = javaToolchains.launcherFor {
            languageVersion = JavaLanguageVersion.of(25)
            vendor = JvmVendorSpec.JETBRAINS
        }.get().metadata.installationPath.asFile.path
        jvmArgs += "--enable-native-access=ALL-UNNAMED"
        if (System.getProperty("os.name").startsWith("Mac")) {
            jvmArgs += listOf("-Dapple.awt.application.name=AvdBuddy", "-Dapple.awt.application.appearance=NSAppearanceNameAqua")
        }
        nativeDistributions {
            targetFormats(TargetFormat.Zip)
            if (System.getProperty("os.name").startsWith("Mac")) targetFormats(TargetFormat.Dmg)
            packageName = "AvdBuddy"
            packageVersion = "1.0.0"
            description = "Android Virtual Device manager"
            vendor = "Alex Styl"
            modules("java.net.http", "java.prefs", "java.xml", "jdk.unsupported")
            publish { github { enabled = true; owner = "alexstyl"; repo = "avdbuddy" } }
            macOS {
                bundleID = "com.alexstyl.avdbuddy"
                iconFile.set(project.file("packaging/app-icon.icns"))
                minimumSystemVersion = "12.0"
                packageBuildVersion = "1000"
                infoPlist {
                    extraKeysRawXml = """
                        <key>SUFeedURL</key><string>https://github.com/alexstyl/avdbuddy/releases/latest/download/appcast.xml</string>
                        <key>SUPublicEDKey</key><string>98Ak6s+hDcV8aKiNMVieV8LTncbT9SuYuhWoFo4WNkI=</string>
                    """.trimIndent()
                }
                // Credentials are read only for an explicitly requested signed release build.
                if (providers.gradleProperty("signedRelease").orNull == "true") {
                    signing { sign.set(true); identity.set(providers.environmentVariable("RELEASE_SIGN_IDENTITY")) }
                    notarization { keychainProfile.set(providers.environmentVariable("NOTARY_KEYCHAIN_PROFILE")) }
                }
            }
        }
    }
}

// Exercise the real command-line entry point without starting Compose or opening a window.
tasks.register<JavaExec>("platformDiagnostics") {
    group = "verification"
    description = "Run application diagnostics headlessly on the current host"
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass.set("com.avdbuddy.MainKt")
    javaLauncher.set(javaToolchains.launcherFor {
        languageVersion = JavaLanguageVersion.of(25)
        vendor = JvmVendorSpec.JETBRAINS
    })
    systemProperty("java.awt.headless", "true")
    args("--diagnostics")
}
