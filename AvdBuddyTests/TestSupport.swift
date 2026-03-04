import Foundation
import Testing

func temporarySDKRoot() throws -> URL {
    let sdkRoot = FileManager().temporaryDirectory.appendingPathComponent(UUID().uuidString)
    try FileManager().createDirectory(at: sdkRoot, withIntermediateDirectories: true)
    return sdkRoot
}

func createSDKToolchainFixture(at sdkRoot: URL) throws {
    let fileManager = FileManager()
    let relativePaths = [
        "cmdline-tools/latest/bin/sdkmanager",
        "cmdline-tools/latest/bin/avdmanager",
        "emulator/emulator",
        "platform-tools/adb"
    ]

    for relativePath in relativePaths {
        let fileURL = sdkRoot.appendingPathComponent(relativePath)
        try fileManager.createDirectory(at: fileURL.deletingLastPathComponent(), withIntermediateDirectories: true)
        try "#!/bin/sh\n".write(to: fileURL, atomically: true, encoding: .utf8)
        try fileManager.setAttributes([.posixPermissions: 0o755], ofItemAtPath: fileURL.path)
    }

    try fileManager.createDirectory(
        at: sdkRoot.appendingPathComponent("skins/pixel_9"),
        withIntermediateDirectories: true
    )
}

func eventually(
    timeoutNanoseconds: UInt64 = 2_000_000_000,
    intervalNanoseconds: UInt64 = 20_000_000,
    condition: @escaping @Sendable () -> Bool
) async throws {
    let deadline = ContinuousClock.now + .nanoseconds(Int64(timeoutNanoseconds))
    while !condition() {
        if ContinuousClock.now >= deadline {
            Issue.record("Condition was not met before timeout.")
            throw CancellationError()
        }
        try await Task.sleep(nanoseconds: intervalNanoseconds)
    }
}
