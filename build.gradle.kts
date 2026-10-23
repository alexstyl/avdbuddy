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
val buildVersion = project.version.toString()
require(buildVersion.matches(Regex("[0-9]+\\.[0-9]+\\.[0-9]+"))) { "Set a stable version in package.yml" }
tasks.processResources {
    inputs.property("buildVersion", buildVersion)
    filesMatching("avdbuddy-build.properties") {
        expand("buildVersion" to buildVersion)
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
                System.getProperty("os.name").startsWith("Mac") -> arrayOf(TargetFormat.Dmg, TargetFormat.Zip)
                System.getProperty("os.name").startsWith("Windows") -> arrayOf(TargetFormat.Nsis)
                else -> arrayOf(TargetFormat.AppImage)
            })
            packageName = "AvdBuddy"
            packageVersion = buildVersion
            windows {
                nsis { oneClick = false; allowToChangeInstallationDirectory = true; runAfterFinish = false }
            }
            linux {
                packageName = "avdbuddy"
                appCategory = "Development"
                debMaintainer = "Alex Styl"
            }
            description = "Android Virtual Device manager"
            vendor = "Alex Styl"
            modules("java.net.http", "java.prefs", "java.xml", "jdk.unsupported")
            publish {
                publishMode = PublishMode.Never
            }
            macOS {
                bundleID = "com.alexstyl.avdbuddy"
                minimumSystemVersion = "12.0"
                packageBuildVersion = buildVersion
                signing {
                    sign.set(providers.environmentVariable("AVDBUDDY_MAC_SIGN_IDENTITY").isPresent)
                    identity.set(providers.environmentVariable("AVDBUDDY_MAC_SIGN_IDENTITY"))
                }
                notarization {
                    keychainProfile.set(providers.environmentVariable("AVDBUDDY_NOTARY_PROFILE"))
                    keychainPath.set(providers.environmentVariable("AVDBUDDY_NOTARY_KEYCHAIN"))
                }
            }
        }
    }
}

// Nucleus signs the app bundle; also sign the disk image before notarizing it.
tasks.matching { it.name == "packageDmg" }.configureEach {
    val identity = providers.environmentVariable("AVDBUDDY_MAC_SIGN_IDENTITY")
    inputs.property("diskImageSigningIdentity", identity.orElse(""))
    doLast {
        if (identity.isPresent) {
            val images = layout.buildDirectory.dir("compose/binaries/main/dmg").get().asFile.listFiles().orEmpty().filter { it.extension == "dmg" }
            check(images.size == 1) { "Expected exactly one DMG to sign" }
            providers.exec { commandLine("/usr/bin/codesign", "--force", "--timestamp", "--sign", identity.get(), images.single().absolutePath) }.result.get().assertNormalExitValue()
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
