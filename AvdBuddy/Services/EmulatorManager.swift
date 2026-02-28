import Combine
import Foundation

@MainActor
final class EmulatorManager: ObservableObject {
    @Published private(set) var emulators: [EmulatorInstance] = []
    @Published private(set) var runningEmulatorNames: Set<String> = []
    @Published private(set) var deletingEmulatorNames: Set<String> = []
    @Published private(set) var totalEmulatorsDiskUsageBytes: Int64 = 0
    @Published var statusMessage: String = ""
    @Published var isBusy: Bool = false
    @Published private(set) var lastCreatedEmulatorName: String?

    private let runner: CommandRunning
    private let fileManager: FileManager
    private let sdkPath: String
    private let avdRootOverride: URL?
    private var createCancellationFlag: CancellationFlag?

    init(
        runner: CommandRunning = ProcessCommandRunner(),
        fileManager: FileManager = FileManager(),
        sdkPath: String = AndroidSDKLocator.sdkPath(),
        avdRootOverride: URL? = nil
    ) {
        self.runner = runner
        self.fileManager = fileManager
        self.sdkPath = sdkPath
        self.avdRootOverride = avdRootOverride
    }

    func refreshEmulators() {
        guard let files = try? fileManager.contentsOfDirectory(at: avdRootURL, includingPropertiesForKeys: nil) else {
            emulators = []
            totalEmulatorsDiskUsageBytes = 0
            return
        }

        let emulatorNames = files
            .filter { $0.pathExtension == "ini" }
            .map { $0.deletingPathExtension().lastPathComponent }
            .sorted { lhs, rhs in
                lhs.localizedStandardCompare(rhs) == .orderedAscending
            }

        var totalSize: Int64 = 0
        emulators = emulatorNames.map { name in
            totalSize += avdDiskUsageBytes(forAvdNamed: name)
            let metadata = avdMetadata(forAvdNamed: name)
            return EmulatorInstance(
                id: name,
                name: name,
                apiLevel: metadata.apiLevel,
                deviceType: metadata.deviceType,
                colorSeed: metadata.colorSeed
            )
        }
        totalEmulatorsDiskUsageBytes = totalSize
    }

    func refreshRunningStates() {
        do {
            runningEmulatorNames = Set(try runningEmulatorSerialsByName().keys)
        } catch {
            runningEmulatorNames = []
        }
    }

    func isRunning(_ emulator: EmulatorInstance) -> Bool {
        runningEmulatorNames.contains(emulator.name)
    }

    func isDeleting(_ emulator: EmulatorInstance) -> Bool {
        deletingEmulatorNames.contains(emulator.name)
    }

    var totalEmulatorsDiskUsageDescription: String {
        let formatter = ByteCountFormatter()
        formatter.countStyle = .file
        return formatter.string(fromByteCount: totalEmulatorsDiskUsageBytes)
    }

    var sdkManagerDebugCommand: String {
        sdkManagerBinary
    }

    func loadSystemImages() async throws -> [AndroidSystemImage] {
        try await loadSystemImagesWithDebugOutput().images
    }

    func loadSystemImagesWithDebugOutput() async throws -> (images: [AndroidSystemImage], output: String) {
        let runner = self.runner
        let executable = sdkManagerBinary
        let result = try await Task.detached(priority: .userInitiated) {
            try runner.run(Command(
                executable: executable,
                arguments: ["--list"]
            ))
        }.value

        let combinedOutput = """
        $ \(executable) --list
        \(result.stdout)
        \(result.stderr)
        """

        return (
            images: AndroidSystemImageCatalog.parse(from: result.stdout),
            output: combinedOutput.trimmingCharacters(in: .whitespacesAndNewlines)
        )
    }

    func createAVD(from configuration: CreateAVDResolvedConfiguration) async -> Bool {
        let result = await createAVDWithDebugOutput(from: configuration)
        return result.didCreate
    }

    func createAVDWithDebugOutput(from configuration: CreateAVDResolvedConfiguration) async -> (didCreate: Bool, output: String) {
        guard !isBusy else { return (false, "Create already in progress.") }
        let avdName = configuration.avdName.trimmingCharacters(in: .whitespacesAndNewlines)
        if let validationMessage = validationMessageForNewName(avdName) {
            statusMessage = validationMessage
            return (false, statusMessage)
        }

        isBusy = true
        defer { isBusy = false }

        do {
            let runner = self.runner
            let sdkPath = self.sdkPath
            let avdRootURL = self.avdRootURL
            let output = try await Task.detached(priority: .userInitiated) {
                try Self.createEmulator(
                    configuration: configuration,
                    runner: runner,
                    sdkPath: sdkPath,
                    avdRootURL: avdRootURL
                )
            }.value
            refreshEmulators()
            lastCreatedEmulatorName = avdName
            statusMessage = "Created \(avdName)."
            return (true, output)
        } catch {
            statusMessage = "Create failed: \(error.localizedDescription)"
            return (false, statusMessage)
        }
    }

    func createAVDStreaming(
        from configuration: CreateAVDResolvedConfiguration,
        onOutput: @escaping @Sendable (String) -> Void
    ) async -> CreateAVDStreamingResult {
        guard !isBusy else { return .failure("Create already in progress.", "Create already in progress.") }
        let avdName = configuration.avdName.trimmingCharacters(in: .whitespacesAndNewlines)
        if let validationMessage = validationMessageForCreateName(avdName) {
            statusMessage = validationMessage
            return .failure(validationMessage, validationMessage)
        }

        let cancellationFlag = CancellationFlag()
        createCancellationFlag = cancellationFlag
        isBusy = true
        defer {
            isBusy = false
            createCancellationFlag = nil
        }

        do {
            let sdkPath = self.sdkPath
            let avdRootURL = self.avdRootURL
            let streamingRunner = self.runner

            if let streamingRunner = streamingRunner as? any StreamingCommandRunning {
                let output = try await Task.detached(priority: .userInitiated) {
                    try Self.createEmulatorStreaming(
                        configuration: configuration,
                        runner: streamingRunner,
                        sdkPath: sdkPath,
                        avdRootURL: avdRootURL,
                        cancellationFlag: cancellationFlag,
                        onOutput: onOutput
                    )
                }.value

                if cancellationFlag.isCancelled {
                    statusMessage = "Create cancelled."
                    return .cancelled(output)
                }

                refreshEmulators()
                lastCreatedEmulatorName = avdName
                statusMessage = "Created \(avdName)."
                return .success(output)
            }

            let fallback = await createAVDWithDebugOutput(from: configuration)
            if fallback.didCreate {
                onOutput(fallback.output)
                return .success(fallback.output)
            }
            return cancellationFlag.isCancelled ? .cancelled(fallback.output) : .failure(statusMessage, fallback.output)
        } catch {
            if cancellationFlag.isCancelled {
                statusMessage = "Create cancelled."
                return .cancelled("Create cancelled.")
            }
            statusMessage = "Create failed: \(error.localizedDescription)"
            return .failure(statusMessage, statusMessage)
        }
    }

    func cancelCreateOperation() {
        createCancellationFlag?.cancel()
    }

    func launch(_ emulator: EmulatorInstance) async {
        guard !isBusy else { return }
        guard !deletingEmulatorNames.contains(emulator.name) else { return }
        isBusy = true
        defer { isBusy = false }

        do {
            try launchEmulator(named: emulator.name)
            runningEmulatorNames.insert(emulator.name)
            statusMessage = "Launched \(emulator.name)."
        } catch {
            statusMessage = "Launch failed: \(error.localizedDescription)"
        }
    }

    func stop(_ emulator: EmulatorInstance) async {
        guard !isBusy else { return }
        guard !deletingEmulatorNames.contains(emulator.name) else { return }
        isBusy = true
        defer { isBusy = false }

        do {
            let didStop = try stopEmulator(named: emulator.name)
            refreshRunningStates()
            if didStop {
                runningEmulatorNames.remove(emulator.name)
                statusMessage = "Stopped \(emulator.name)."
            } else {
                statusMessage = "\(emulator.name) is not running."
            }
        } catch {
            statusMessage = "Stop failed: \(error.localizedDescription)"
        }
    }

    func delete(_ emulator: EmulatorInstance) async {
        guard !deletingEmulatorNames.contains(emulator.name) else { return }
        deletingEmulatorNames.insert(emulator.name)
        defer { deletingEmulatorNames.remove(emulator.name) }

        do {
            let avdName = emulator.name
            let runner = self.runner
            let sdkPath = self.sdkPath

            try await Task.detached(priority: .userInitiated) {
                try Self.deleteEmulator(named: avdName, runner: runner, sdkPath: sdkPath)
            }.value

            refreshEmulators()
            runningEmulatorNames.remove(emulator.name)
            statusMessage = "Deleted \(emulator.name)."
        } catch {
            statusMessage = "Delete failed: \(error.localizedDescription)"
        }
    }

    func duplicate(_ emulator: EmulatorInstance) async {
        guard !isBusy else { return }
        guard !deletingEmulatorNames.contains(emulator.name) else { return }

        isBusy = true
        defer { isBusy = false }

        do {
            let duplicatedName = try duplicateEmulator(named: emulator.name)
            refreshEmulators()
            statusMessage = "Duplicated \(emulator.name) as \(duplicatedName)."
        } catch {
            statusMessage = "Duplicate failed: \(error.localizedDescription)"
        }
    }

    func rename(_ emulator: EmulatorInstance, to newName: String) async {
        guard !isBusy else { return }
        guard !deletingEmulatorNames.contains(emulator.name) else { return }

        let trimmedName = newName.trimmingCharacters(in: .whitespacesAndNewlines)
        guard let validationMessage = validationMessageForRename(from: emulator.name, to: trimmedName) else {
            isBusy = true
            defer { isBusy = false }

            do {
                try renameEmulator(from: emulator.name, to: trimmedName)
                refreshEmulators()
                refreshRunningStates()
                statusMessage = "Renamed \(emulator.name) to \(trimmedName)."
            } catch {
                statusMessage = "Rename failed: \(error.localizedDescription)"
            }
            return
        }

        statusMessage = validationMessage
    }

    func validationMessageForRename(from currentName: String, to newName: String) -> String? {
        let trimmedName = newName.trimmingCharacters(in: .whitespacesAndNewlines)
        if let validationMessage = validationMessageForNewName(trimmedName) {
            return validationMessage
        }
        if trimmedName == currentName {
            return "Choose a different name."
        }
        if emulators.contains(where: { $0.name == trimmedName }) {
            return "An emulator named \(trimmedName) already exists."
        }
        return nil
    }

    func validationMessageForCreateName(_ newName: String) -> String? {
        let trimmedName = newName.trimmingCharacters(in: .whitespacesAndNewlines)
        if let validationMessage = validationMessageForNewName(trimmedName) {
            return validationMessage
        }
        if emulators.contains(where: { $0.name == trimmedName }) {
            return "An emulator named \(trimmedName) already exists."
        }
        if fileManager.fileExists(atPath: avdRootURL.appendingPathComponent("\(trimmedName).ini").path) ||
            fileManager.fileExists(atPath: avdDirectoryURL(for: trimmedName).path) {
            return "An emulator named \(trimmedName) already exists."
        }
        return nil
    }

    func killAllRunningEmulators() async {
        guard !isBusy else { return }
        isBusy = true
        defer { isBusy = false }

        do {
            let serialByName = try runningEmulatorSerialsByName()
            guard !serialByName.isEmpty else {
                runningEmulatorNames = []
                statusMessage = "No running emulators."
                return
            }

            for serial in serialByName.values.sorted() {
                try killEmulator(serial: serial)
            }

            runningEmulatorNames = []
            statusMessage = "Stopped \(serialByName.count) emulator\(serialByName.count == 1 ? "" : "s")."
        } catch {
            statusMessage = "Kill all failed: \(error.localizedDescription)"
        }
    }

    private func duplicateEmulator(named avdName: String) throws -> String {
        let duplicatedName = nextAvailableDuplicateName(for: avdName)
        let sourceDirectory = avdDirectoryURL(for: avdName)
        let destinationDirectory = avdDirectoryURL(for: duplicatedName)
        let duplicatedColorSeed = EmulatorInstance.fallbackColorSeed(for: duplicatedName)

        try fileManager.copyItem(at: sourceDirectory, to: destinationDirectory)
        try duplicateIniFile(from: avdName, to: duplicatedName)
        try normalizeAVDDirectory(
            at: destinationDirectory,
            oldName: avdName,
            newName: duplicatedName,
            colorSeed: duplicatedColorSeed
        )

        return duplicatedName
    }

    private func renameEmulator(from oldName: String, to newName: String) throws {
        let sourceDirectory = avdDirectoryURL(for: oldName)
        let destinationDirectory = avdDirectoryURL(for: newName)
        let sourceIniURL = avdRootURL.appendingPathComponent("\(oldName).ini")
        let destinationIniURL = avdRootURL.appendingPathComponent("\(newName).ini")

        try fileManager.moveItem(at: sourceDirectory, to: destinationDirectory)
        do {
            try fileManager.moveItem(at: sourceIniURL, to: destinationIniURL)
            try rewriteIniFile(at: destinationIniURL, avdName: newName)
            try normalizeAVDDirectory(at: destinationDirectory, oldName: oldName, newName: newName)
        } catch {
            if fileManager.fileExists(atPath: destinationDirectory.path) &&
               !fileManager.fileExists(atPath: sourceDirectory.path) {
                try? fileManager.moveItem(at: destinationDirectory, to: sourceDirectory)
            }
            throw error
        }
    }

    private nonisolated static func createEmulator(
        configuration: CreateAVDResolvedConfiguration,
        runner: any CommandRunning,
        sdkPath: String,
        avdRootURL: URL
    ) throws -> String {
        let sdkManager = "\(sdkPath)/cmdline-tools/latest/bin/sdkmanager"
        let avdManager = "\(sdkPath)/cmdline-tools/latest/bin/avdmanager"

        let installResult = try runner.run(Command(
            executable: sdkManager,
            arguments: ["--install", configuration.packagePath],
            stdin: String(repeating: "y\n", count: 32)
        ))

        var createArguments = [
            "create", "avd",
            "-n", configuration.avdName,
            "-k", configuration.packagePath,
            "-d", configuration.deviceProfileID
        ]
        if let sdCard = configuration.sdCard {
            createArguments.append(contentsOf: ["-c", sdCard])
        }

        let createResult = try runner.run(Command(
            executable: avdManager,
            arguments: createArguments,
            stdin: "no\n"
        ))

        let configURL = avdRootURL
            .appendingPathComponent("\(configuration.avdName).avd")
            .appendingPathComponent("config.ini")
        try apply(configuration: configuration, to: configURL)

        return """
        $ \(sdkManager) --install \(configuration.packagePath)
        \(installResult.stdout)
        \(installResult.stderr)

        $ \(avdManager) \(createArguments.joined(separator: " "))
        \(createResult.stdout)
        \(createResult.stderr)
        """.trimmingCharacters(in: .whitespacesAndNewlines)
    }

    private nonisolated static func createEmulatorStreaming(
        configuration: CreateAVDResolvedConfiguration,
        runner: any StreamingCommandRunning,
        sdkPath: String,
        avdRootURL: URL,
        cancellationFlag: CancellationFlag,
        onOutput: @escaping @Sendable (String) -> Void
    ) throws -> String {
        let sdkManager = "\(sdkPath)/cmdline-tools/latest/bin/sdkmanager"
        let avdManager = "\(sdkPath)/cmdline-tools/latest/bin/avdmanager"

        var combinedOutput = "$ \(sdkManager) --install \(configuration.packagePath)\n"
        onOutput(combinedOutput)

        let installResult = try runner.runStreaming(
            Command(
                executable: sdkManager,
                arguments: ["--install", configuration.packagePath],
                stdin: String(repeating: "y\n", count: 32)
            ),
            onOutput: { chunk in
                onOutput(chunk)
            },
            shouldCancel: { cancellationFlag.isCancelled }
        )
        combinedOutput += installResult.stdout + installResult.stderr

        if cancellationFlag.isCancelled {
            return combinedOutput
        }

        var createArguments = [
            "create", "avd",
            "-n", configuration.avdName,
            "-k", configuration.packagePath,
            "-d", configuration.deviceProfileID
        ]
        if let sdCard = configuration.sdCard {
            createArguments.append(contentsOf: ["-c", sdCard])
        }

        let createHeader = "\n\n$ \(avdManager) \(createArguments.joined(separator: " "))\n"
        combinedOutput += createHeader
        onOutput(createHeader)

        let createResult = try runner.runStreaming(
            Command(
                executable: avdManager,
                arguments: createArguments,
                stdin: "no\n"
            ),
            onOutput: { chunk in
                onOutput(chunk)
            },
            shouldCancel: { cancellationFlag.isCancelled }
        )
        combinedOutput += createResult.stdout + createResult.stderr

        if cancellationFlag.isCancelled {
            return combinedOutput
        }

        let configURL = avdRootURL
            .appendingPathComponent("\(configuration.avdName).avd")
            .appendingPathComponent("config.ini")
        try apply(configuration: configuration, to: configURL)

        return combinedOutput.trimmingCharacters(in: .whitespacesAndNewlines)
    }

    private func duplicateIniFile(from oldName: String, to newName: String) throws {
        let sourceIniURL = avdRootURL.appendingPathComponent("\(oldName).ini")
        let destinationIniURL = avdRootURL.appendingPathComponent("\(newName).ini")
        try fileManager.copyItem(at: sourceIniURL, to: destinationIniURL)
        try rewriteIniFile(at: destinationIniURL, avdName: newName)
    }

    private func rewriteIniFile(at iniURL: URL, avdName: String) throws {
        let absolutePath = avdDirectoryURL(for: avdName).path
        var lines = (try? String(contentsOf: iniURL, encoding: .utf8))?
            .split(whereSeparator: \.isNewline)
            .map(String.init) ?? []

        replaceOrAppendLine(prefix: "path=", with: "path=\(absolutePath)", in: &lines)
        replaceOrAppendLine(prefix: "path.rel=", with: "path.rel=avd/\(avdName).avd", in: &lines)

        try (lines.joined(separator: "\n") + "\n").write(to: iniURL, atomically: true, encoding: .utf8)
    }

    private func normalizeAVDDirectory(
        at directoryURL: URL,
        oldName: String,
        newName: String,
        colorSeed: String? = nil
    ) throws {
        let oldDirectoryPath = avdDirectoryURL(for: oldName).path
        let newDirectoryPath = directoryURL.path

        try rewriteConfigDisplayName(
            at: directoryURL.appendingPathComponent("config.ini"),
            newName: newName,
            colorSeed: colorSeed
        )
        try rewriteTextFiles(
            under: directoryURL,
            oldName: oldName,
            newName: newName,
            oldDirectoryPath: oldDirectoryPath,
            newDirectoryPath: newDirectoryPath
        )
        try removeTransientArtifacts(in: directoryURL)
    }

    private func rewriteConfigDisplayName(at configURL: URL, newName: String, colorSeed: String? = nil) throws {
        guard fileManager.fileExists(atPath: configURL.path) else { return }
        var lines = (try String(contentsOf: configURL, encoding: .utf8))
            .split(whereSeparator: \.isNewline)
            .map(String.init)

        replaceOrAppendLine(prefix: "avd.ini.displayname=", with: "avd.ini.displayname=\(newName)", in: &lines)
        if let colorSeed {
            replaceOrAppendLine(prefix: "avdbuddy.color.seed=", with: "avdbuddy.color.seed=\(colorSeed)", in: &lines)
        }
        try (lines.joined(separator: "\n") + "\n").write(to: configURL, atomically: true, encoding: .utf8)
    }

    private func rewriteTextFiles(
        under rootURL: URL,
        oldName: String,
        newName: String,
        oldDirectoryPath: String,
        newDirectoryPath: String
    ) throws {
        guard let enumerator = fileManager.enumerator(at: rootURL, includingPropertiesForKeys: [.isRegularFileKey]) else {
            return
        }

        for case let fileURL as URL in enumerator {
            guard let values = try? fileURL.resourceValues(forKeys: [.isRegularFileKey]),
                  values.isRegularFile == true else {
                continue
            }

            let pathExtension = fileURL.pathExtension.lowercased()
            guard ["ini", "txt", "conf"].contains(pathExtension) else {
                continue
            }

            guard var contents = try? String(contentsOf: fileURL, encoding: .utf8) else {
                continue
            }

            contents = contents.replacingOccurrences(of: oldDirectoryPath, with: newDirectoryPath)
            contents = contents.replacingOccurrences(of: oldName, with: newName)

            try contents.write(to: fileURL, atomically: true, encoding: .utf8)
        }
    }

    private func removeTransientArtifacts(in directoryURL: URL) throws {
        let transientPaths = [
            "hardware-qemu.ini",
            "multiinstance.lock",
            "read-snapshot.txt",
            "emu-launch-params.txt",
            "tmpAdbCmds",
            "cache.img.qcow2",
            "userdata-qemu.img.qcow2",
            "encryptionkey.img.qcow2",
            "snapshots"
        ]

        for relativePath in transientPaths {
            let artifactURL = directoryURL.appendingPathComponent(relativePath)
            if fileManager.fileExists(atPath: artifactURL.path) {
                try? fileManager.removeItem(at: artifactURL)
            }
        }

        guard let fileURLs = try? fileManager.contentsOfDirectory(at: directoryURL, includingPropertiesForKeys: nil) else {
            return
        }

        for fileURL in fileURLs where fileURL.lastPathComponent.contains(".tmp-") {
            try? fileManager.removeItem(at: fileURL)
        }
    }

    private func launchEmulator(named avdName: String) throws {
        let emulatorBinary = "\(sdkPath)/emulator/emulator"
        _ = try runner.run(Command(
            executable: emulatorBinary,
            arguments: ["-avd", avdName],
            waitForExit: false
        ))
    }

    private func stopEmulator(named avdName: String) throws -> Bool {
        guard let serial = try runningEmulatorSerialsByName()[avdName] else {
            return false
        }

        try killEmulator(serial: serial)
        return true
    }

    private nonisolated static func deleteEmulator(named avdName: String, runner: any CommandRunning, sdkPath: String) throws {
        let avdManager = "\(sdkPath)/cmdline-tools/latest/bin/avdmanager"
        _ = try runner.run(Command(
            executable: avdManager,
            arguments: ["delete", "avd", "-n", avdName]
        ))
    }

    private func avdMetadata(forAvdNamed avdName: String) -> (apiLevel: Int?, deviceType: EmulatorDeviceType, colorSeed: String?) {
        let configURL = avdDirectoryURL(for: avdName)
            .appendingPathComponent("config.ini")

        guard let config = try? String(contentsOf: configURL) else {
            return (nil, .unknown, nil)
        }
        return (
            apiLevel: AVDConfigParser.apiLevel(from: config),
            deviceType: AVDConfigParser.deviceType(from: config),
            colorSeed: AVDConfigParser.colorSeed(from: config)
        )
    }

    private func avdDiskUsageBytes(forAvdNamed avdName: String) -> Int64 {
        let avdDirectory = avdDirectoryURL(for: avdName)

        guard let enumerator = fileManager.enumerator(
            at: avdDirectory,
            includingPropertiesForKeys: [.isRegularFileKey, .fileSizeKey]
        ) else {
            return 0
        }

        var totalSize: Int64 = 0
        for case let fileURL as URL in enumerator {
            guard let values = try? fileURL.resourceValues(forKeys: [.isRegularFileKey, .fileSizeKey]),
                  values.isRegularFile == true,
                  let fileSize = values.fileSize else {
                continue
            }
            totalSize += Int64(fileSize)
        }
        return totalSize
    }

    private func runningEmulatorSerialsByName() throws -> [String: String] {
        let adbBinary = "\(sdkPath)/platform-tools/adb"
        let devicesOutput = try runner.run(Command(
            executable: adbBinary,
            arguments: ["devices"]
        )).stdout

        let serials = devicesOutput
            .split(whereSeparator: \.isNewline)
            .map(String.init)
            .compactMap { line -> String? in
                let trimmed = line.trimmingCharacters(in: .whitespacesAndNewlines)
                guard trimmed.hasPrefix("emulator-"), trimmed.hasSuffix("\tdevice") else { return nil }
                return String(trimmed.split(separator: "\t", maxSplits: 1).first ?? "")
            }

        var serialByName: [String: String] = [:]
        for serial in serials {
            guard let avdName = avdName(forEmulatorSerial: serial, adbBinary: adbBinary) else {
                continue
            }
            serialByName[avdName] = serial
        }

        return serialByName
    }

    private func avdName(forEmulatorSerial serial: String, adbBinary: String) -> String? {
        if let output = try? runner.run(Command(
            executable: adbBinary,
            arguments: ["-s", serial, "shell", "getprop", "ro.boot.qemu.avd_name"]
        )).stdout,
           let name = parseAVDName(from: output) {
            return name
        }

        if let output = try? runner.run(Command(
            executable: adbBinary,
            arguments: ["-s", serial, "emu", "avd", "name"]
        )).stdout,
           let name = parseAVDName(from: output) {
            return name
        }

        return nil
    }

    private func parseAVDName(from output: String) -> String? {
        output
            .split(whereSeparator: \.isNewline)
            .map { $0.trimmingCharacters(in: .whitespacesAndNewlines) }
            .first { line in
                !line.isEmpty && line != "OK" && !line.hasPrefix("KO:")
            }
    }

    private func killEmulator(serial: String) throws {
        let adbBinary = "\(sdkPath)/platform-tools/adb"
        _ = try runner.run(Command(
            executable: adbBinary,
            arguments: ["-s", serial, "emu", "kill"]
        ))
    }

    private var avdRootURL: URL {
        if let avdRootOverride {
            return avdRootOverride
        }
        return fileManager.homeDirectoryForCurrentUser
            .appendingPathComponent(".android")
            .appendingPathComponent("avd")
    }

    private var sdkManagerBinary: String {
        "\(sdkPath)/cmdline-tools/latest/bin/sdkmanager"
    }

    private nonisolated static func apply(
        configuration: CreateAVDResolvedConfiguration,
        to configURL: URL
    ) throws {
        let fileManager = FileManager()
        guard fileManager.fileExists(atPath: configURL.path) else { return }
        var lines = (try String(contentsOf: configURL, encoding: .utf8))
            .split(whereSeparator: \.isNewline)
            .map(String.init)

        replaceOrAppendLine(prefix: "disk.dataPartition.size=", with: "disk.dataPartition.size=\(configuration.storage)", in: &lines)
        replaceOrAppendLine(prefix: "avd.ini.displayname=", with: "avd.ini.displayname=\(configuration.avdName)", in: &lines)
        replaceOrAppendLine(prefix: "avdbuddy.color.seed=", with: "avdbuddy.color.seed=\(configuration.colorSeed)", in: &lines)
        if let ramMB = configuration.ramMB {
            replaceOrAppendLine(prefix: "hw.ramSize=", with: "hw.ramSize=\(ramMB)", in: &lines)
        }

        try (lines.joined(separator: "\n") + "\n").write(to: configURL, atomically: true, encoding: .utf8)
    }

    private func avdDirectoryURL(for avdName: String) -> URL {
        avdRootURL.appendingPathComponent("\(avdName).avd")
    }

    private func nextAvailableDuplicateName(for avdName: String) -> String {
        let existingNames = Set(emulators.map(\.name))
        let baseName = "\(avdName)_Copy"
        if !existingNames.contains(baseName) {
            return baseName
        }

        var counter = 2
        while existingNames.contains("\(baseName) \(counter)") {
            counter += 1
        }
        return "\(baseName) \(counter)"
    }

    private nonisolated static func replaceOrAppendLine(prefix: String, with replacement: String, in lines: inout [String]) {
        if let index = lines.firstIndex(where: { $0.hasPrefix(prefix) }) {
            lines[index] = replacement
        } else {
            lines.append(replacement)
        }
    }

    private func replaceOrAppendLine(prefix: String, with replacement: String, in lines: inout [String]) {
        Self.replaceOrAppendLine(prefix: prefix, with: replacement, in: &lines)
    }

    private func validationMessageForNewName(_ newName: String) -> String? {
        if newName.isEmpty {
            return "Please enter an emulator name."
        }
        let allowedCharacters = CharacterSet.alphanumerics.union(CharacterSet(charactersIn: "._-"))
        if newName.rangeOfCharacter(from: allowedCharacters.inverted) != nil {
            return "Use only letters, numbers, dots, underscores, or dashes."
        }
        return nil
    }
}

enum CreateAVDStreamingResult {
    case success(String)
    case failure(String, String)
    case cancelled(String)
}

final class CancellationFlag: @unchecked Sendable {
    private let lock = NSLock()
    private var cancelled = false

    var isCancelled: Bool {
        lock.lock()
        defer { lock.unlock() }
        return cancelled
    }

    func cancel() {
        lock.lock()
        cancelled = true
        lock.unlock()
    }
}
