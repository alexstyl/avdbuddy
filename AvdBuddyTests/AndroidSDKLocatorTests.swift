import Foundation
import Testing
@testable import AvdBuddy

struct AndroidSDKLocatorTests {
    @Test
    func resolvesVersionedCmdlineToolsWhenLatestIsMissing() throws {
        let sdkRoot = FileManager().temporaryDirectory.appendingPathComponent(UUID().uuidString)
        defer { try? FileManager().removeItem(at: sdkRoot) }

        try createExecutable(at: sdkRoot.appendingPathComponent("cmdline-tools/12.0/bin/sdkmanager"))
        try createExecutable(at: sdkRoot.appendingPathComponent("cmdline-tools/12.0/bin/avdmanager"))
        try createExecutable(at: sdkRoot.appendingPathComponent("emulator/emulator"))
        try createExecutable(at: sdkRoot.appendingPathComponent("platform-tools/adb"))

        let toolchain = AndroidSDKLocator.resolveToolchain(for: sdkRoot.path)
        let status = AndroidSDKLocator.toolchainStatus(for: sdkRoot.path, isStoredOverride: false)

        #expect(toolchain.sdkManager == "\(sdkRoot.path)/cmdline-tools/12.0/bin/sdkmanager")
        #expect(toolchain.avdManager == "\(sdkRoot.path)/cmdline-tools/12.0/bin/avdmanager")
        #expect(status.isConfigured)
    }

    @Test
    func rejectsLegacyToolsBinForSdkManagerAndAvdManager() throws {
        let sdkRoot = FileManager().temporaryDirectory.appendingPathComponent(UUID().uuidString)
        defer { try? FileManager().removeItem(at: sdkRoot) }

        try createExecutable(at: sdkRoot.appendingPathComponent("tools/bin/sdkmanager"))
        try createExecutable(at: sdkRoot.appendingPathComponent("tools/bin/avdmanager"))
        try createExecutable(at: sdkRoot.appendingPathComponent("emulator/emulator"))
        try createExecutable(at: sdkRoot.appendingPathComponent("platform-tools/adb"))

        let status = AndroidSDKLocator.toolchainStatus(for: sdkRoot.path, isStoredOverride: false)

        #expect(!status.isConfigured)
        #expect(status.unsupportedTools.map(\.tool) == [.sdkManager, .avdManager])
        #expect(status.summary.contains("Deprecated sdkmanager, avdmanager found under tools/bin."))
    }

    @Test
    func reportsMissingToolsForIncompleteSDK() throws {
        let sdkRoot = FileManager().temporaryDirectory.appendingPathComponent(UUID().uuidString)
        defer { try? FileManager().removeItem(at: sdkRoot) }

        try createExecutable(at: sdkRoot.appendingPathComponent("cmdline-tools/latest/bin/sdkmanager"))
        try createExecutable(at: sdkRoot.appendingPathComponent("cmdline-tools/latest/bin/avdmanager"))

        let status = AndroidSDKLocator.toolchainStatus(for: sdkRoot.path, isStoredOverride: false)

        #expect(!status.isConfigured)
        #expect(status.missingTools.map(\.tool) == [.emulator, .adb])
    }
}

private func createExecutable(at url: URL) throws {
    let fileManager = FileManager()
    try fileManager.createDirectory(at: url.deletingLastPathComponent(), withIntermediateDirectories: true)
    try "#!/bin/sh\n".write(to: url, atomically: true, encoding: .utf8)
    try fileManager.setAttributes([.posixPermissions: 0o755], ofItemAtPath: url.path)
}
