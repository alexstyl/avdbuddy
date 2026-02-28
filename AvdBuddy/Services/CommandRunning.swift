import Foundation

struct Command {
    let executable: String
    let arguments: [String]
    let stdin: String?
    let waitForExit: Bool

    init(
        executable: String,
        arguments: [String],
        stdin: String? = nil,
        waitForExit: Bool = true
    ) {
        self.executable = executable
        self.arguments = arguments
        self.stdin = stdin
        self.waitForExit = waitForExit
    }
}

struct CommandResult {
    let exitCode: Int32
    let stdout: String
    let stderr: String
}

protocol CommandRunning: Sendable {
    @discardableResult
    func run(_ command: Command) throws -> CommandResult
}

protocol StreamingCommandRunning: CommandRunning {
    @discardableResult
    func runStreaming(
        _ command: Command,
        onOutput: @escaping @Sendable (String) -> Void,
        shouldCancel: @escaping @Sendable () -> Bool
    ) throws -> CommandResult
}

enum CommandError: Error, LocalizedError {
    case executableNotFound(String)
    case nonZeroExit(CommandResult)

    var errorDescription: String? {
        switch self {
        case .executableNotFound(let executable):
            return "Executable not found: \(executable)"
        case .nonZeroExit(let result):
            return "Command failed with exit code \(result.exitCode): \(result.stderr)"
        }
    }
}

final class ProcessCommandRunner: StreamingCommandRunning, @unchecked Sendable {
    @discardableResult
    func run(_ command: Command) throws -> CommandResult {
        let process = Process()
        process.executableURL = URL(fileURLWithPath: command.executable)
        process.arguments = command.arguments

        let outputPipe: Pipe?
        let errorPipe: Pipe?
        if command.waitForExit {
            let capturedOutput = Pipe()
            let capturedError = Pipe()
            process.standardOutput = capturedOutput
            process.standardError = capturedError
            outputPipe = capturedOutput
            errorPipe = capturedError
        } else {
            process.standardOutput = FileHandle.nullDevice
            process.standardError = FileHandle.nullDevice
            outputPipe = nil
            errorPipe = nil
        }

        if let stdin = command.stdin {
            let inputPipe = Pipe()
            process.standardInput = inputPipe
            try process.run()
            if let data = stdin.data(using: .utf8) {
                inputPipe.fileHandleForWriting.write(data)
            }
            inputPipe.fileHandleForWriting.closeFile()
        } else {
            try process.run()
        }

        if !command.waitForExit {
            return CommandResult(exitCode: 0, stdout: "", stderr: "")
        }

        let (stdoutData, stderrData) = try readOutputData(stdoutPipe: outputPipe, stderrPipe: errorPipe)

        process.waitUntilExit()

        let result = CommandResult(
            exitCode: process.terminationStatus,
            stdout: String(decoding: stdoutData, as: UTF8.self),
            stderr: String(decoding: stderrData, as: UTF8.self)
        )

        guard result.exitCode == 0 else {
            throw CommandError.nonZeroExit(result)
        }

        return result
    }

    @discardableResult
    func runStreaming(
        _ command: Command,
        onOutput: @escaping @Sendable (String) -> Void,
        shouldCancel: @escaping @Sendable () -> Bool
    ) throws -> CommandResult {
        let process = Process()
        process.executableURL = URL(fileURLWithPath: command.executable)
        process.arguments = command.arguments

        let outputPipe = Pipe()
        let errorPipe = Pipe()
        process.standardOutput = outputPipe
        process.standardError = errorPipe

        let stdoutState = LockedDataState()
        let stderrState = LockedDataState()
        let streamGroup = DispatchGroup()
        let streamError = LockedErrorState()

        installReadabilityHandler(
            for: outputPipe.fileHandleForReading,
            state: stdoutState,
            group: streamGroup,
            streamError: streamError,
            onOutput: onOutput
        )
        installReadabilityHandler(
            for: errorPipe.fileHandleForReading,
            state: stderrState,
            group: streamGroup,
            streamError: streamError,
            onOutput: onOutput
        )

        if let stdin = command.stdin {
            let inputPipe = Pipe()
            process.standardInput = inputPipe
            try process.run()
            if let data = stdin.data(using: .utf8) {
                inputPipe.fileHandleForWriting.write(data)
            }
            inputPipe.fileHandleForWriting.closeFile()
        } else {
            try process.run()
        }

        while process.isRunning {
            if shouldCancel() {
                process.terminate()
                break
            }
            Thread.sleep(forTimeInterval: 0.05)
        }

        process.waitUntilExit()
        streamGroup.wait()

        if let readError = streamError.value {
            throw readError
        }

        let result = CommandResult(
            exitCode: process.terminationStatus,
            stdout: String(decoding: stdoutState.value, as: UTF8.self),
            stderr: String(decoding: stderrState.value, as: UTF8.self)
        )

        guard result.exitCode == 0 else {
            throw CommandError.nonZeroExit(result)
        }

        return result
    }

    private func readOutputData(stdoutPipe: Pipe?, stderrPipe: Pipe?) throws -> (Data, Data) {
        let stdoutState = LockedDataState()
        let stderrState = LockedDataState()
        let group = DispatchGroup()
        let errorState = LockedErrorState()

        for (pipe, state) in [(stdoutPipe, stdoutState), (stderrPipe, stderrState)] {
            guard let pipe else { continue }
            group.enter()
            DispatchQueue.global(qos: .userInitiated).async {
                defer { group.leave() }
                do {
                    let data = try pipe.fileHandleForReading.readToEnd() ?? Data()
                    state.set(data)
                } catch {
                    errorState.set(error)
                }
            }
        }

        group.wait()

        if let readError = errorState.value {
            throw readError
        }

        return (stdoutState.value, stderrState.value)
    }

    private func installReadabilityHandler(
        for handle: FileHandle,
        state: LockedDataState,
        group: DispatchGroup,
        streamError: LockedErrorState,
        onOutput: @escaping @Sendable (String) -> Void
    ) {
        group.enter()
        handle.readabilityHandler = { fileHandle in
            let data = fileHandle.availableData
            if data.isEmpty {
                fileHandle.readabilityHandler = nil
                group.leave()
                return
            }

            state.append(data)
            let text = String(decoding: data, as: UTF8.self)
            if !text.isEmpty {
                onOutput(text)
            }
        }
    }
}

private final class LockedDataState: @unchecked Sendable {
    private let lock = NSLock()
    private var storage = Data()

    var value: Data {
        lock.lock()
        defer { lock.unlock() }
        return storage
    }

    func set(_ data: Data) {
        lock.lock()
        storage = data
        lock.unlock()
    }

    func append(_ data: Data) {
        lock.lock()
        storage.append(data)
        lock.unlock()
    }
}

private final class LockedErrorState: @unchecked Sendable {
    private let lock = NSLock()
    private var storage: Error?

    var value: Error? {
        lock.lock()
        defer { lock.unlock() }
        return storage
    }

    func set(_ error: Error) {
        lock.lock()
        if storage == nil {
            storage = error
        }
        lock.unlock()
    }
}
