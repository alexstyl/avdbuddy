import dev.nucleusframework.desktop.application.dsl.TargetFormat
import dev.nucleusframework.desktop.application.dsl.PublishMode
plugins {
    kotlin("jvm") version "2.4.0"
    kotlin("plugin.compose") version "2.4.0"
    id("org.jetbrains.compose") version "1.12.0"
    id("org.jetbrains.compose.hot-reload") version "1.2.0"
    id("dev.nucleusframework") version "2.5.18"
    id("com.composables.kpm") version "0.2.0"
    id("com.google.protobuf") version "0.10.0"
}
val nightlyBuild = providers.gradleProperty("nightlyBuild").map(String::toBoolean).getOrElse(false)
val nightlyBuildNumber = providers.gradleProperty("nightlyBuildNumber").getOrElse("0")
val buildVersion = if (nightlyBuild) providers.gradleProperty("nightlyVersion").orElse(providers.exec {
    commandLine("git", "rev-parse", "--short=7", "HEAD")
}.standardOutput.asText.map { "nightly.${it.trim()}" }).get() else "1.0.0"
val buildChannel = if (nightlyBuild) "nightly" else "stable"
tasks.processResources {
    inputs.property("buildChannel", buildChannel)
    inputs.property("buildVersion", buildVersion)
    filesMatching("avdbuddy-build.properties") {
        expand("buildChannel" to buildChannel, "buildVersion" to buildVersion)
    }
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
    inputs.property("installedSdkSmoke", providers.environmentVariable("AVDBUDDY_TEST_INSTALLED_SDK").orElse("false"))
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
            targetFormats(*when {
                nightlyBuild && System.getProperty("os.name").startsWith("Mac") -> arrayOf(TargetFormat.Dmg)
                nightlyBuild && System.getProperty("os.name").startsWith("Windows") -> arrayOf(TargetFormat.Nsis)
                nightlyBuild -> arrayOf(TargetFormat.AppImage)
                System.getProperty("os.name").startsWith("Mac") -> arrayOf(TargetFormat.Dmg, TargetFormat.Zip)
                else -> arrayOf(TargetFormat.Zip)
            })
            packageName = if (nightlyBuild) "AvdBuddy-Nightly" else "AvdBuddy"
            packageVersion = if (nightlyBuild) "1.0.$nightlyBuildNumber" else "1.0.0"
            homepage = "https://github.com/alexstyl/avdbuddy"
            if (nightlyBuild) artifactName = "AvdBuddy-Nightly-$buildVersion-\${os}-\${arch}.\${ext}"
            windows {
                if (nightlyBuild) {
                    upgradeUuid = "cbbfaefb-0277-46f2-8352-d76324d6067a"
                    nsis { oneClick = false; allowToChangeInstallationDirectory = true; runAfterFinish = false }
                }
            }
            linux {
                packageName = if (nightlyBuild) "avdbuddy-nightly" else "avdbuddy"
                appCategory = "Development"
                debMaintainer = "Alex Styl"
            }
            description = "Android Virtual Device manager"
            vendor = "Alex Styl"
            modules("java.net.http", "java.prefs", "java.xml", "jdk.unsupported")
            publish {
                publishMode = PublishMode.Never
                github { enabled = !nightlyBuild; owner = "alexstyl"; repo = "avdbuddy" }
            }
            macOS {
                bundleID = if (nightlyBuild) "com.alexstyl.avdbuddy.nightly" else "com.alexstyl.avdbuddy"
                iconFile.set(project.file("packaging/app-icon.icns"))
                minimumSystemVersion = "12.0"
                packageBuildVersion = if (nightlyBuild) nightlyBuildNumber else "1000"
                infoPlist {
                    extraKeysRawXml = if (nightlyBuild) "" else """
                        <key>SUFeedURL</key><string>https://github.com/alexstyl/avdbuddy/releases/latest/download/appcast.xml</string>
                        <key>SUPublicEDKey</key><string>98Ak6s+hDcV8aKiNMVieV8LTncbT9SuYuhWoFo4WNkI=</string>
                    """.trimIndent()
                }
                // Credentials are read only for an explicitly requested signed release build.
                if (!nightlyBuild && providers.gradleProperty("signedRelease").orNull == "true") {
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
