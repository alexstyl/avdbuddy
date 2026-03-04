import AppKit
import Foundation

enum AppDiagnostics {
    struct CommandProbeResult: Sendable {
        let command: String
        let exitCode: Int32?
        let stdout: String
        let stderr: String
        let errorDescription: String?

        var summary: String {
            if let errorDescription {
                return "failed to launch: \(errorDescription)"
            }
            return "exit \(exitCode ?? -1)"
        }
    }

    struct Context: Sendable {
        let installationSource: AppInstallationSource
        let toolchainStatus: AndroidToolchainStatus
        let autodetectedSDKPath: String?
        let environment: [String: String]
        let bundlePath: String
        let appVersion: String
        let macOSVersion: String
        let sdkLayout: SDKLayoutSummary
        let localSystemImages: LocalSystemImageSummary
        let availableDeviceFrames: DeviceFrameSummary
        let sdkManagerVersion: CommandProbeResult?
        let sdkManagerList: CommandProbeResult?
        let avdManagerListDevices: CommandProbeResult?
    }

    struct SDKLayoutSummary: Sendable {
        let cmdlineToolsLatestExists: Bool
        let legacyToolsBinExists: Bool
        let emulatorBinaryExists: Bool
        let adbBinaryExists: Bool
    }

    struct LocalSystemImageSummary: Sendable {
        let count: Int
        let samplePackages: [String]
    }

    struct DeviceFrameSummary: Sendable {
        let availableCount: Int
        let availableProfiles: [String]
    }

    static func collectContext(
        bundle: Bundle = .main,
        processInfo: ProcessInfo = .processInfo,
        installationSource: AppInstallationSource = .detect(),
        toolchainStatus: AndroidToolchainStatus = AndroidSDKLocator.toolchainStatus(),
        autodetectedSDKPath: String? = AndroidSDKLocator.autodetectedSDKPath(),
        environment: [String: String] = ProcessInfo.processInfo.environment,
        fileManager: FileManager = .default
    ) -> Context {
        let toolchain = AndroidSDKLocator.resolveToolchain(for: toolchainStatus.sdkPath, fileManager: fileManager)

        return Context(
            installationSource: installationSource,
            toolchainStatus: toolchainStatus,
            autodetectedSDKPath: autodetectedSDKPath,
            environment: environment,
            bundlePath: bundle.bundleURL.path,
            appVersion: appVersion(from: bundle),
            macOSVersion: macOSVersion(from: processInfo),
            sdkLayout: sdkLayoutSummary(sdkPath: toolchainStatus.sdkPath, fileManager: fileManager),
            localSystemImages: localSystemImageSummary(sdkPath: toolchainStatus.sdkPath, fileManager: fileManager),
            availableDeviceFrames: deviceFrameSummary(sdkPath: toolchain.sdkPath, fileManager: fileManager),
            sdkManagerVersion: probeCommand(executable: toolchain.sdkManager, arguments: ["--version"]),
            sdkManagerList: probeCommand(executable: toolchain.sdkManager, arguments: ["--list"]),
            avdManagerListDevices: probeCommand(executable: toolchain.avdManager, arguments: ["list", "device"])
        )
    }

    static func report(
        context: Context,
        maxOutputCharacters: Int = 6_000
    ) -> String {
        let redactor = PathRedactor(homeDirectoryPath: FileManager.default.homeDirectoryForCurrentUser.path)
        let lines = [
            "AvdBuddy Diagnostics",
            "App version: \(context.appVersion)",
            "macOS: \(context.macOSVersion)",
            "Installation source: \(context.installationSource.description)",
            "Bundle path: \(redactor.redact(context.bundlePath))",
            "SDK path: \(redactor.redact(context.toolchainStatus.sdkPath))",
            "Autodetected SDK path: \(redactor.redact(context.autodetectedSDKPath ?? "Unavailable"))",
            "ANDROID_SDK_ROOT: \(redactor.redact(context.environment["ANDROID_SDK_ROOT"] ?? "Unset"))",
            "ANDROID_HOME: \(redactor.redact(context.environment["ANDROID_HOME"] ?? "Unset"))",
            "Toolchain status: \(context.toolchainStatus.summary)",
            "sdkmanager: \(toolDescription(for: .sdkManager, in: context.toolchainStatus, redactor: redactor))",
            "avdmanager: \(toolDescription(for: .avdManager, in: context.toolchainStatus, redactor: redactor))",
            "emulator: \(toolDescription(for: .emulator, in: context.toolchainStatus, redactor: redactor))",
            "adb: \(toolDescription(for: .adb, in: context.toolchainStatus, redactor: redactor))",
            "cmdline-tools/latest present: \(yesNo(context.sdkLayout.cmdlineToolsLatestExists))",
            "legacy tools/bin present: \(yesNo(context.sdkLayout.legacyToolsBinExists))",
            "emulator binary present: \(yesNo(context.sdkLayout.emulatorBinaryExists))",
            "adb binary present: \(yesNo(context.sdkLayout.adbBinaryExists))",
            "Local system images found: \(context.localSystemImages.count)",
            "Local system image sample: \(context.localSystemImages.samplePackages.joined(separator: ", ").ifEmpty("None"))",
            "Device frame profiles available: \(context.availableDeviceFrames.availableCount)",
            "Device frame profile sample: \(context.availableDeviceFrames.availableProfiles.joined(separator: ", ").ifEmpty("None"))",
            commandSection(title: "sdkmanager --version", result: context.sdkManagerVersion, maxOutputCharacters: maxOutputCharacters, redactor: redactor),
            commandSection(title: "sdkmanager --list", result: context.sdkManagerList, maxOutputCharacters: maxOutputCharacters, redactor: redactor),
            commandSection(title: "avdmanager list device", result: context.avdManagerListDevices, maxOutputCharacters: maxOutputCharacters, redactor: redactor)
        ]

        return lines.joined(separator: "\n")
    }

    static func report(
        bundle: Bundle = .main,
        processInfo: ProcessInfo = .processInfo,
        installationSource: AppInstallationSource = .detect(),
        toolchainStatus: AndroidToolchainStatus = AndroidSDKLocator.toolchainStatus(),
        autodetectedSDKPath: String? = AndroidSDKLocator.autodetectedSDKPath(),
        environment: [String: String] = ProcessInfo.processInfo.environment,
        fileManager: FileManager = .default,
        maxOutputCharacters: Int = 6_000
    ) -> String {
        report(
            context: collectContext(
                bundle: bundle,
                processInfo: processInfo,
                installationSource: installationSource,
                toolchainStatus: toolchainStatus,
                autodetectedSDKPath: autodetectedSDKPath,
                environment: environment,
                fileManager: fileManager
            ),
            maxOutputCharacters: maxOutputCharacters
        )
    }

    static func copyReport(
        context: Context,
        pasteboard: NSPasteboard = .general,
        maxOutputCharacters: Int = 6_000
    ) {
        let text = report(context: context, maxOutputCharacters: maxOutputCharacters)
        pasteboard.clearContents()
        pasteboard.setString(text, forType: .string)
    }

    static func copyReport(
        bundle: Bundle = .main,
        processInfo: ProcessInfo = .processInfo,
        installationSource: AppInstallationSource = .detect(),
        toolchainStatus: AndroidToolchainStatus = AndroidSDKLocator.toolchainStatus(),
        autodetectedSDKPath: String? = AndroidSDKLocator.autodetectedSDKPath(),
        environment: [String: String] = ProcessInfo.processInfo.environment,
        fileManager: FileManager = .default,
        pasteboard: NSPasteboard = .general,
        maxOutputCharacters: Int = 6_000
    ) {
        copyReport(
            context: collectContext(
                bundle: bundle,
                processInfo: processInfo,
                installationSource: installationSource,
                toolchainStatus: toolchainStatus,
                autodetectedSDKPath: autodetectedSDKPath,
                environment: environment,
                fileManager: fileManager
            ),
            pasteboard: pasteboard,
            maxOutputCharacters: maxOutputCharacters
        )
    }

    private static func probeCommand(
        executable: String,
        arguments: [String],
        stdin: String? = nil
    ) -> CommandProbeResult {
        let process = Process()
        process.executableURL = URL(fileURLWithPath: executable)
        process.arguments = arguments

        let stdoutPipe = Pipe()
        let stderrPipe = Pipe()
        process.standardOutput = stdoutPipe
        process.standardError = stderrPipe

        if let stdin {
            let inputPipe = Pipe()
            process.standardInput = inputPipe
            do {
                try process.run()
                if let data = stdin.data(using: .utf8) {
                    inputPipe.fileHandleForWriting.write(data)
                }
                inputPipe.fileHandleForWriting.closeFile()
            } catch {
                return CommandProbeResult(
                    command: renderCommand(executable: executable, arguments: arguments),
                    exitCode: nil,
                    stdout: "",
                    stderr: "",
                    errorDescription: error.localizedDescription
                )
            }
        } else {
            do {
                try process.run()
            } catch {
                return CommandProbeResult(
                    command: renderCommand(executable: executable, arguments: arguments),
                    exitCode: nil,
                    stdout: "",
                    stderr: "",
                    errorDescription: error.localizedDescription
                )
            }
        }

        let stdoutData = (try? stdoutPipe.fileHandleForReading.readToEnd()) ?? Data()
        let stderrData = (try? stderrPipe.fileHandleForReading.readToEnd()) ?? Data()
        process.waitUntilExit()

        return CommandProbeResult(
            command: renderCommand(executable: executable, arguments: arguments),
            exitCode: process.terminationStatus,
            stdout: String(decoding: stdoutData, as: UTF8.self),
            stderr: String(decoding: stderrData, as: UTF8.self),
            errorDescription: nil
        )
    }

    private static func sdkLayoutSummary(sdkPath: String, fileManager: FileManager) -> SDKLayoutSummary {
        SDKLayoutSummary(
            cmdlineToolsLatestExists: fileManager.fileExists(atPath: "\(sdkPath)/cmdline-tools/latest/bin"),
            legacyToolsBinExists: fileManager.fileExists(atPath: "\(sdkPath)/tools/bin"),
            emulatorBinaryExists: fileManager.isExecutableFile(atPath: "\(sdkPath)/emulator/emulator"),
            adbBinaryExists: fileManager.isExecutableFile(atPath: "\(sdkPath)/platform-tools/adb")
        )
    }

    private static func localSystemImageSummary(sdkPath: String, fileManager: FileManager) -> LocalSystemImageSummary {
        let systemImagesRoot = URL(fileURLWithPath: sdkPath).appendingPathComponent("system-images")
        guard let versionDirectories = try? fileManager.contentsOfDirectory(
            at: systemImagesRoot,
            includingPropertiesForKeys: [.isDirectoryKey],
            options: [.skipsHiddenFiles]
        ) else {
            return LocalSystemImageSummary(count: 0, samplePackages: [])
        }

        var packages: [String] = []
        for versionDirectory in versionDirectories.sorted(by: { $0.lastPathComponent.localizedStandardCompare($1.lastPathComponent) == .orderedAscending }) {
            guard isDirectory(versionDirectory, fileManager: fileManager) else { continue }
            guard let tagDirectories = try? fileManager.contentsOfDirectory(
                at: versionDirectory,
                includingPropertiesForKeys: [.isDirectoryKey],
                options: [.skipsHiddenFiles]
            ) else {
                continue
            }

            for tagDirectory in tagDirectories.sorted(by: { $0.lastPathComponent.localizedStandardCompare($1.lastPathComponent) == .orderedAscending }) {
                guard isDirectory(tagDirectory, fileManager: fileManager) else { continue }
                guard let abiDirectories = try? fileManager.contentsOfDirectory(
                    at: tagDirectory,
                    includingPropertiesForKeys: [.isDirectoryKey],
                    options: [.skipsHiddenFiles]
                ) else {
                    continue
                }

                for abiDirectory in abiDirectories.sorted(by: { $0.lastPathComponent.localizedStandardCompare($1.lastPathComponent) == .orderedAscending }) {
                    guard isDirectory(abiDirectory, fileManager: fileManager) else { continue }
                    packages.append("system-images;\(versionDirectory.lastPathComponent);\(tagDirectory.lastPathComponent);\(abiDirectory.lastPathComponent)")
                }
            }
        }

        return LocalSystemImageSummary(
            count: packages.count,
            samplePackages: Array(packages.prefix(12))
        )
    }

    private static func deviceFrameSummary(sdkPath: String, fileManager: FileManager) -> DeviceFrameSummary {
        let manager = EmulatorManager(fileManager: fileManager, sdkPath: sdkPath)
        let supportedProfiles = CreateAVDDeviceType.allCases
            .flatMap(\.profileOptions)
            .filter { profile in
                return manager.hasUsableDeviceFrame(for: profile.id)
            }
            .map(\.id)

        return DeviceFrameSummary(
            availableCount: supportedProfiles.count,
            availableProfiles: Array(supportedProfiles.prefix(12))
        )
    }

    private static func commandSection(
        title: String,
        result: CommandProbeResult?,
        maxOutputCharacters: Int,
        redactor: PathRedactor
    ) -> String {
        guard let result else {
            return "\(title): not collected"
        }

        let stdout = truncate(redactor.redact(result.stdout), maxCharacters: maxOutputCharacters)
        let stderr = truncate(redactor.redact(result.stderr), maxCharacters: maxOutputCharacters)

        return """
        \(title): \(result.summary)
        Command: \(redactor.redact(result.command))
        Stdout:
        \(stdout.ifEmpty("<empty>"))
        Stderr:
        \(stderr.ifEmpty("<empty>"))
        """
    }

    private static func truncate(_ value: String, maxCharacters: Int) -> String {
        let trimmed = value.trimmingCharacters(in: .whitespacesAndNewlines)
        guard trimmed.count > maxCharacters else { return trimmed }
        let endIndex = trimmed.index(trimmed.startIndex, offsetBy: maxCharacters)
        return "\(trimmed[..<endIndex])\n[truncated]"
    }

    private static func toolDescription(for tool: AndroidTool, in status: AndroidToolchainStatus, redactor: PathRedactor) -> String {
        guard let toolState = status.toolStates.first(where: { $0.tool == tool }) else {
            return "Unavailable"
        }
        let availability: String
        switch toolState.validationStatus {
        case .available:
            availability = "available"
        case .missing:
            availability = "missing"
        case .unsupported:
            availability = "unsupported"
        }
        return "\(redactor.redact(toolState.path)) (\(availability))"
    }

    private static func renderCommand(executable: String, arguments: [String]) -> String {
        ([executable] + arguments).joined(separator: " ")
    }

    private static func appVersion(from bundle: Bundle) -> String {
        let shortVersion = bundle.object(forInfoDictionaryKey: "CFBundleShortVersionString") as? String ?? "1.0"
        let build = bundle.object(forInfoDictionaryKey: "CFBundleVersion") as? String ?? "1"
        return "\(shortVersion) (\(build))"
    }

    private static func macOSVersion(from processInfo: ProcessInfo) -> String {
        let version = processInfo.operatingSystemVersion
        return "\(version.majorVersion).\(version.minorVersion).\(version.patchVersion)"
    }

    private static func isDirectory(_ url: URL, fileManager: FileManager) -> Bool {
        var isDirectory: ObjCBool = false
        return fileManager.fileExists(atPath: url.path, isDirectory: &isDirectory) && isDirectory.boolValue
    }

    private static func yesNo(_ value: Bool) -> String {
        value ? "Yes" : "No"
    }
}

private struct PathRedactor {
    let homeDirectoryPath: String

    func redact(_ value: String) -> String {
        guard !homeDirectoryPath.isEmpty else { return value }
        return value.replacingOccurrences(of: homeDirectoryPath, with: "~")
    }
}

private extension AppInstallationSource {
    var description: String {
        switch self {
        case .direct:
            return "Direct"
        case .homebrew:
            return "Homebrew"
        }
    }
}

private extension String {
    func ifEmpty(_ fallback: String) -> String {
        isEmpty ? fallback : self
    }
}
