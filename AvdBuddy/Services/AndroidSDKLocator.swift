import Foundation

enum AndroidSDKSettings {
    static let sdkPathOverrideKey = "android.sdk.path.override"

    static func storedSDKPath(userDefaults: UserDefaults = .standard) -> String? {
        let value = userDefaults.string(forKey: sdkPathOverrideKey)?
            .trimmingCharacters(in: .whitespacesAndNewlines)
        guard let value, !value.isEmpty else { return nil }
        return value
    }

    static func setStoredSDKPath(_ sdkPath: String?, userDefaults: UserDefaults = .standard) {
        let trimmed = sdkPath?.trimmingCharacters(in: .whitespacesAndNewlines)
        if let trimmed, !trimmed.isEmpty {
            userDefaults.set(trimmed, forKey: sdkPathOverrideKey)
        } else {
            userDefaults.removeObject(forKey: sdkPathOverrideKey)
        }
    }
}

enum AndroidTool: String, CaseIterable, Identifiable, Sendable {
    case sdkManager
    case avdManager
    case emulator
    case adb

    var id: String { rawValue }

    var title: String {
        switch self {
        case .sdkManager: "sdkmanager"
        case .avdManager: "avdmanager"
        case .emulator: "emulator"
        case .adb: "adb"
        }
    }
}

struct AndroidToolState: Identifiable, Equatable, Sendable {
    enum ValidationStatus: Equatable, Sendable {
        case available
        case missing
        case unsupported(String)
    }

    let tool: AndroidTool
    let path: String
    let validationStatus: ValidationStatus

    var isAvailable: Bool {
        validationStatus == .available
    }

    var isUnsupported: Bool {
        if case .unsupported = validationStatus {
            return true
        }
        return false
    }

    var issueDescription: String? {
        switch validationStatus {
        case .available:
            return nil
        case .missing:
            return "Not found in this SDK."
        case .unsupported(let message):
            return message
        }
    }

    var id: AndroidTool { tool }
}

struct AndroidToolchain: Equatable, Sendable {
    let sdkPath: String
    let sdkManager: String
    let avdManager: String
    let emulator: String
    let adb: String

    func path(for tool: AndroidTool) -> String {
        switch tool {
        case .sdkManager: sdkManager
        case .avdManager: avdManager
        case .emulator: emulator
        case .adb: adb
        }
    }
}

struct AndroidToolchainStatus: Equatable, Sendable {
    let sdkPath: String
    let isStoredOverride: Bool
    let toolStates: [AndroidToolState]

    var isConfigured: Bool {
        toolStates.allSatisfy(\.isAvailable)
    }

    var missingTools: [AndroidToolState] {
        toolStates.filter { $0.validationStatus == .missing }
    }

    var unsupportedTools: [AndroidToolState] {
        toolStates.filter(\.isUnsupported)
    }

    var summary: String {
        guard !isConfigured else {
            return "Android SDK ready."
        }
        if !unsupportedTools.isEmpty {
            let names = unsupportedTools.map { $0.tool.title }.joined(separator: ", ")
            if missingTools.isEmpty {
                return "Deprecated \(names) found under tools/bin. Install Android Command-line Tools."
            }
            let missingNames = missingTools.map { $0.tool.title }.joined(separator: ", ")
            return "Deprecated \(names) found under tools/bin. Missing \(missingNames). Install Android Command-line Tools."
        }
        guard !missingTools.isEmpty else {
            return "Android SDK setup is incomplete."
        }
        let names = missingTools.map { $0.tool.title }.joined(separator: ", ")
        return "Missing \(names)."
    }

    func actionMessage(for action: String) -> String {
        guard !isConfigured else {
            return "Android SDK ready."
        }
        return "\(action) requires a configured Android SDK. \(summary)"
    }
}

enum AndroidToolchainError: Error, LocalizedError {
    case notConfigured(String)

    var errorDescription: String? {
        switch self {
        case .notConfigured(let message):
            return message
        }
    }
}

enum AndroidSDKLocator {
    static func preferredSDKPath(
        userDefaults: UserDefaults = .standard,
        environment: [String: String] = ProcessInfo.processInfo.environment,
        fileManager: FileManager = FileManager()
    ) -> String {
        if let stored = AndroidSDKSettings.storedSDKPath(userDefaults: userDefaults) {
            return stored
        }
        return autodetectedSDKPath(environment: environment, fileManager: fileManager) ?? defaultSDKPath(environment: environment)
    }

    static func sdkPath(environment: [String: String] = ProcessInfo.processInfo.environment) -> String {
        preferredSDKPath(environment: environment)
    }

    static func autodetectedSDKPath(
        environment: [String: String] = ProcessInfo.processInfo.environment,
        fileManager: FileManager = FileManager()
    ) -> String? {
        let candidates = candidateSDKPaths(environment: environment, fileManager: fileManager)
        if let fullyConfigured = candidates.first(where: { toolchainStatus(for: $0, isStoredOverride: false, fileManager: fileManager).isConfigured }) {
            return fullyConfigured
        }
        if let existingDirectory = candidates.first(where: { fileManager.fileExists(atPath: $0, isDirectory: nil) }) {
            return existingDirectory
        }
        return candidates.first
    }

    static func candidateSDKPaths(
        environment: [String: String] = ProcessInfo.processInfo.environment,
        fileManager: FileManager = FileManager()
    ) -> [String] {
        let home = fileManager.homeDirectoryForCurrentUser.path
        let candidates = [
            environment["ANDROID_SDK_ROOT"],
            environment["ANDROID_HOME"],
            "\(home)/Library/Android/sdk"
        ]

        var unique: [String] = []
        for case let candidate? in candidates {
            let trimmed = candidate.trimmingCharacters(in: .whitespacesAndNewlines)
            guard !trimmed.isEmpty else { continue }
            if !unique.contains(trimmed) {
                unique.append(trimmed)
            }
        }
        return unique
    }

    static func toolchainStatus(
        preferredSDKPath: String? = AndroidSDKSettings.storedSDKPath(),
        environment: [String: String] = ProcessInfo.processInfo.environment,
        fileManager: FileManager = FileManager(),
        userDefaults: UserDefaults = .standard
    ) -> AndroidToolchainStatus {
        let storedOverride = AndroidSDKSettings.storedSDKPath(userDefaults: userDefaults)
        let sdkPath = preferredSDKPath?.trimmingCharacters(in: .whitespacesAndNewlines).nilIfEmpty
            ?? storedOverride
            ?? autodetectedSDKPath(environment: environment, fileManager: fileManager)
            ?? defaultSDKPath(environment: environment)

        return toolchainStatus(
            for: sdkPath,
            isStoredOverride: storedOverride == sdkPath,
            fileManager: fileManager
        )
    }

    static func toolchainStatus(
        for sdkPath: String,
        isStoredOverride: Bool,
        fileManager: FileManager = FileManager()
    ) -> AndroidToolchainStatus {
        let toolchain = resolveToolchain(for: sdkPath, fileManager: fileManager)
        let toolStates = AndroidTool.allCases.map { tool in
            let path = toolchain.path(for: tool)
            return AndroidToolState(
                tool: tool,
                path: path,
                validationStatus: validationStatus(
                    for: tool,
                    path: path,
                    sdkPath: sdkPath,
                    fileManager: fileManager
                )
            )
        }
        return AndroidToolchainStatus(
            sdkPath: sdkPath,
            isStoredOverride: isStoredOverride,
            toolStates: toolStates
        )
    }

    static func resolveToolchain(
        for sdkPath: String,
        fileManager: FileManager = FileManager()
    ) -> AndroidToolchain {
        AndroidToolchain(
            sdkPath: sdkPath,
            sdkManager: cmdlineToolBinary(named: "sdkmanager", sdkPath: sdkPath, fileManager: fileManager),
            avdManager: cmdlineToolBinary(named: "avdmanager", sdkPath: sdkPath, fileManager: fileManager),
            emulator: preferredPath(
                primary: "\(sdkPath)/emulator/emulator",
                fallbacks: [],
                fileManager: fileManager
            ),
            adb: preferredPath(
                primary: "\(sdkPath)/platform-tools/adb",
                fallbacks: [],
                fileManager: fileManager
            )
        )
    }

    private static func defaultSDKPath(environment: [String: String]) -> String {
        if let sdkRoot = environment["ANDROID_SDK_ROOT"], !sdkRoot.isEmpty {
            return sdkRoot
        }
        if let androidHome = environment["ANDROID_HOME"], !androidHome.isEmpty {
            return androidHome
        }
        let home = FileManager().homeDirectoryForCurrentUser.path
        return "\(home)/Library/Android/sdk"
    }

    private static func cmdlineToolBinary(
        named binaryName: String,
        sdkPath: String,
        fileManager: FileManager
    ) -> String {
        let latestPath = "\(sdkPath)/cmdline-tools/latest/bin/\(binaryName)"
        let toolDirectories = cmdlineToolDirectories(sdkPath: sdkPath, fileManager: fileManager)
        let fallbacks = toolDirectories.map { "\($0)/bin/\(binaryName)" } + ["\(sdkPath)/tools/bin/\(binaryName)"]
        return preferredPath(primary: latestPath, fallbacks: fallbacks, fileManager: fileManager)
    }

    private static func cmdlineToolDirectories(
        sdkPath: String,
        fileManager: FileManager
    ) -> [String] {
        let cmdlineToolsRoot = "\(sdkPath)/cmdline-tools"
        guard let children = try? fileManager.contentsOfDirectory(atPath: cmdlineToolsRoot) else {
            return []
        }

        return children
            .map { "\(cmdlineToolsRoot)/\($0)" }
            .filter { path in
                var isDirectory: ObjCBool = false
                return fileManager.fileExists(atPath: path, isDirectory: &isDirectory) && isDirectory.boolValue
            }
            .sorted { lhs, rhs in
                lhs.localizedStandardCompare(rhs) == .orderedDescending
            }
    }

    private static func preferredPath(
        primary: String,
        fallbacks: [String],
        fileManager: FileManager
    ) -> String {
        if fileManager.isExecutableFile(atPath: primary) {
            return primary
        }
        for fallback in fallbacks where fileManager.isExecutableFile(atPath: fallback) {
            return fallback
        }
        return primary
    }

    private static func validationStatus(
        for tool: AndroidTool,
        path: String,
        sdkPath: String,
        fileManager: FileManager
    ) -> AndroidToolState.ValidationStatus {
        guard fileManager.isExecutableFile(atPath: path) else {
            return .missing
        }

        if [.sdkManager, .avdManager].contains(tool), isLegacyToolsBinary(path, sdkPath: sdkPath) {
            return .unsupported("Invalid binary. AvdBuddy requires cmdline-tools.")
        }

        return .available
    }

    private static func isLegacyToolsBinary(_ path: String, sdkPath: String) -> Bool {
        let normalizedPath = URL(fileURLWithPath: path).standardizedFileURL.path
        let normalizedSDKPath = URL(fileURLWithPath: sdkPath).standardizedFileURL.path
        return normalizedPath.hasPrefix("\(normalizedSDKPath)/tools/bin/")
    }
}

private extension String {
    var nilIfEmpty: String? {
        isEmpty ? nil : self
    }
}
