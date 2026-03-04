import AppKit
import Foundation
import Testing
@testable import AvdBuddy

struct AppDiagnosticsTests {
    @Test
    func reportIncludesWizardCriticalDiagnostics() {
        let home = FileManager.default.homeDirectoryForCurrentUser.path
        let context = AppDiagnostics.Context(
            installationSource: .homebrew,
            toolchainStatus: AndroidToolchainStatus(
                sdkPath: "\(home)/Library/Android/sdk",
                isStoredOverride: false,
                toolStates: [
                    AndroidToolState(tool: .sdkManager, path: "\(home)/Library/Android/sdk/cmdline-tools/latest/bin/sdkmanager", validationStatus: .available),
                    AndroidToolState(tool: .avdManager, path: "\(home)/Library/Android/sdk/cmdline-tools/latest/bin/avdmanager", validationStatus: .available),
                    AndroidToolState(tool: .emulator, path: "\(home)/Library/Android/sdk/emulator/emulator", validationStatus: .missing),
                    AndroidToolState(tool: .adb, path: "\(home)/Library/Android/sdk/platform-tools/adb", validationStatus: .available),
                ]
            ),
            autodetectedSDKPath: "\(home)/Library/Android/sdk",
            environment: [
                "ANDROID_SDK_ROOT": "\(home)/Library/Android/sdk",
                "ANDROID_HOME": "\(home)/Library/Android/sdk"
            ],
            bundlePath: "\(home)/Applications/AvdBuddy.app",
            appVersion: "0.4.0 (12)",
            macOSVersion: "26.3.0",
            sdkLayout: AppDiagnostics.SDKLayoutSummary(
                cmdlineToolsLatestExists: true,
                legacyToolsBinExists: true,
                emulatorBinaryExists: false,
                adbBinaryExists: true
            ),
            localSystemImages: AppDiagnostics.LocalSystemImageSummary(
                count: 3,
                samplePackages: [
                    "system-images;android-35;google_apis;arm64-v8a",
                    "system-images;android-36;google_apis_playstore;arm64-v8a"
                ]
            ),
            availableDeviceFrames: AppDiagnostics.DeviceFrameSummary(
                availableCount: 2,
                availableProfiles: ["pixel_9", "pixel_9_pro_xl"]
            ),
            sdkManagerVersion: AppDiagnostics.CommandProbeResult(
                command: "\(home)/Library/Android/sdk/cmdline-tools/latest/bin/sdkmanager --version",
                exitCode: 0,
                stdout: "12.0",
                stderr: "",
                errorDescription: nil
            ),
            sdkManagerList: AppDiagnostics.CommandProbeResult(
                command: "\(home)/Library/Android/sdk/cmdline-tools/latest/bin/sdkmanager --list",
                exitCode: 1,
                stdout: "Installed packages:\n\(home)/Library/Android/sdk/system-images/android-35/google_apis/arm64-v8a",
                stderr: "java.lang.RuntimeException: repository load failed",
                errorDescription: nil
            ),
            avdManagerListDevices: AppDiagnostics.CommandProbeResult(
                command: "\(home)/Library/Android/sdk/cmdline-tools/latest/bin/avdmanager list device",
                exitCode: 0,
                stdout: "id: 0 or \"pixel_9\"",
                stderr: "",
                errorDescription: nil
            )
        )

        let report = AppDiagnostics.report(context: context, maxOutputCharacters: 200)

        #expect(report.contains("Installation source: Homebrew"))
        #expect(report.contains("legacy tools/bin present: Yes"))
        #expect(report.contains("Local system images found: 3"))
        #expect(report.contains("Device frame profiles available: 2"))
        #expect(report.contains("sdkmanager --list: exit 1"))
        #expect(report.contains("java.lang.RuntimeException: repository load failed"))
        #expect(report.contains("avdmanager list device: exit 0"))
        #expect(!report.contains(home))
        #expect(report.contains("SDK path: ~/Library/Android/sdk"))
    }

    @Test
    func copyReportWritesDiagnosticsToPasteboard() {
        let pasteboard = NSPasteboard.withUniqueName()
        let context = AppDiagnostics.Context(
            installationSource: .direct,
            toolchainStatus: AndroidToolchainStatus(
                sdkPath: "/sdk",
                isStoredOverride: false,
                toolStates: [
                    AndroidToolState(tool: .sdkManager, path: "/sdk/sdkmanager", validationStatus: .available),
                    AndroidToolState(tool: .avdManager, path: "/sdk/avdmanager", validationStatus: .available),
                    AndroidToolState(tool: .emulator, path: "/sdk/emulator", validationStatus: .available),
                    AndroidToolState(tool: .adb, path: "/sdk/adb", validationStatus: .available),
                ]
            ),
            autodetectedSDKPath: "/sdk",
            environment: [:],
            bundlePath: "/tmp/AvdBuddy.app",
            appVersion: "1.0 (1)",
            macOSVersion: "26.3.0",
            sdkLayout: AppDiagnostics.SDKLayoutSummary(
                cmdlineToolsLatestExists: true,
                legacyToolsBinExists: false,
                emulatorBinaryExists: true,
                adbBinaryExists: true
            ),
            localSystemImages: AppDiagnostics.LocalSystemImageSummary(count: 1, samplePackages: ["system-images;android-36;google_apis;arm64-v8a"]),
            availableDeviceFrames: AppDiagnostics.DeviceFrameSummary(availableCount: 1, availableProfiles: ["pixel_9"]),
            sdkManagerVersion: nil,
            sdkManagerList: nil,
            avdManagerListDevices: nil
        )

        AppDiagnostics.copyReport(context: context, pasteboard: pasteboard)

        let contents = pasteboard.string(forType: .string)
        #expect(contents?.contains("AvdBuddy Diagnostics") == true)
        #expect(contents?.contains("Installation source: Direct") == true)
        #expect(contents?.contains("sdkmanager --list: not collected") == true)
    }
}
