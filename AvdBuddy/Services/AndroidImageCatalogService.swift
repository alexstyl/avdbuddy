import Foundation

struct AndroidImageCatalogLoadResult: Sendable {
    let sdkPath: String
    let command: String
    let images: [AndroidSystemImage]
    let output: String
}

enum AndroidImageCatalogState: Sendable {
    case idle
    case loading
    case loaded(AndroidImageCatalogLoadResult)
    case failed(message: String, output: String)

    var images: [AndroidSystemImage] {
        switch self {
        case .loaded(let result):
            return result.images
        default:
            return []
        }
    }

    var output: String {
        switch self {
        case .loaded(let result):
            return result.output
        case .failed(_, let output):
            return output
        default:
            return ""
        }
    }

    var isLoading: Bool {
        if case .loading = self {
            return true
        }
        return false
    }

    func isLoaded(for sdkPath: String) -> Bool {
        if case .loaded(let result) = self {
            return result.sdkPath == sdkPath
        }
        return false
    }
}

struct AndroidImageCatalogRequest: Sendable {
    let sdkPath: String
    let sdkManagerExecutable: String
    let runner: CommandRunning
}

struct AndroidImageCatalogLoadError: Error, Sendable {
    let output: String
    let underlyingErrorDescription: String
}

actor AndroidImageCatalogService {
    private var generation = 0
    private var snapshot: AndroidImageCatalogLoadResult?
    private var task: Task<AndroidImageCatalogLoadResult, Error>?
    private var taskGeneration: Int?

    func invalidate() {
        generation += 1
        task?.cancel()
        task = nil
        taskGeneration = nil
        snapshot = nil
    }

    func warmIfNeeded(using request: AndroidImageCatalogRequest) {
        guard snapshot?.sdkPath != request.sdkPath else { return }
        guard task == nil else { return }

        let currentGeneration = generation
        taskGeneration = currentGeneration
        task = makeTask(using: request)
    }

    func load(using request: AndroidImageCatalogRequest) async throws -> AndroidImageCatalogLoadResult {
        if let snapshot, snapshot.sdkPath == request.sdkPath {
            return snapshot
        }

        if task == nil {
            let currentGeneration = generation
            taskGeneration = currentGeneration
            task = makeTask(using: request)
        }

        let currentTask = task
        let currentGeneration = taskGeneration

        do {
            let result = try await currentTask?.value
            guard let result else {
                throw AndroidImageCatalogLoadError(
                    output: "",
                    underlyingErrorDescription: "Catalog load task was unavailable."
                )
            }

            if generation == currentGeneration, result.sdkPath == request.sdkPath {
                snapshot = result
                task = nil
                taskGeneration = nil
            }

            return result
        } catch {
            if generation == currentGeneration {
                task = nil
                taskGeneration = nil
            }
            throw error
        }
    }

    private func makeTask(using request: AndroidImageCatalogRequest) -> Task<AndroidImageCatalogLoadResult, Error> {
        Task.detached(priority: .utility) {
            let command = Command(
                executable: request.sdkManagerExecutable,
                arguments: ["--list"]
            )

            do {
                let result = try request.runner.run(command)
                let output = Self.renderOutput(
                    executable: request.sdkManagerExecutable,
                    result: result
                )

                return AndroidImageCatalogLoadResult(
                    sdkPath: request.sdkPath,
                    command: "\(request.sdkManagerExecutable) --list",
                    images: AndroidSystemImageCatalog.parse(from: result.stdout),
                    output: output
                )
            } catch let commandError as CommandError {
                throw AndroidImageCatalogLoadError(
                    output: Self.renderOutput(
                        executable: request.sdkManagerExecutable,
                        result: commandError.result
                    ),
                    underlyingErrorDescription: commandError.localizedDescription
                )
            } catch {
                throw AndroidImageCatalogLoadError(
                    output: """
                    $ \(request.sdkManagerExecutable) --list
                    \(error.localizedDescription)
                    """.trimmingCharacters(in: .whitespacesAndNewlines),
                    underlyingErrorDescription: error.localizedDescription
                )
            }
        }
    }

    private static func renderOutput(executable: String, result: CommandResult) -> String {
        """
        $ \(executable) --list
        \(result.stdout)
        \(result.stderr)
        """.trimmingCharacters(in: .whitespacesAndNewlines)
    }
}

private extension CommandError {
    var result: CommandResult {
        switch self {
        case .nonZeroExit(let result):
            return result
        case .executableNotFound(let executable):
            return CommandResult(exitCode: 127, stdout: "", stderr: "Executable not found: \(executable)")
        }
    }
}
