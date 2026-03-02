import Foundation
import Testing
@testable import AvdBuddy

struct CreateAVDModelsTests {
    @Test
    func generatedSuggestedNamesAreEmulatorSafe() {
        for deviceType in CreateAVDDeviceType.allCases {
            let name = deviceType.randomSuggestedName()
            let parts = name.split(separator: "_")

            #expect(!name.isEmpty)
            #expect(parts.count == 2)
            #expect(name.range(of: #"^[A-Za-z0-9._-]+$"#, options: .regularExpression) != nil)
        }
    }

    @Test
    func exposesExpectedWizardFormFactors() {
        #expect(CreateAVDDeviceType.allCases.map(\.rawValue) == [
            "Phone",
            "Foldable",
            "Tablet",
            "Wear OS",
            "Desktop",
            "TV",
            "Automotive",
            "XR"
        ])
    }

    @Test
    func exposesExpectedPhoneProfiles() {
        #expect(CreateAVDDeviceType.phone.profileOptions.map(\.id) == [
            "pixel_9",
            "pixel_9a",
            "pixel_9_pro",
            "pixel_9_pro_xl"
        ])
    }

    @Test
    func exposesExpectedFoldableProfiles() {
        #expect(CreateAVDDeviceType.foldable.profileOptions.map(\.id) == [
            "pixel_9_pro_fold",
            "pixel_fold"
        ])
    }

    @Test
    func exposesExpectedAutomotiveProfiles() {
        #expect(CreateAVDDeviceType.automotive.profileOptions.map(\.id) == [
            "automotive_1080p_landscape",
            "automotive_1024p_landscape",
            "automotive_1408p_landscape_with_google_apis",
            "automotive_distant_display",
            "automotive_large_portrait",
            "automotive_portrait",
            "automotive_ultrawide"
        ])
    }

    @Test @MainActor
    func defaultsDeviceFrameBackOnWhenSwitchingToSupportedFormFactor() throws {
        let sdkRoot = try temporarySDKRootForModels()
        defer { try? FileManager().removeItem(at: sdkRoot) }
        try createSDKToolchainFixtureForModels(at: sdkRoot)
        try FileManager().createDirectory(
            at: sdkRoot.appendingPathComponent("skins/pixel_9"),
            withIntermediateDirectories: true
        )
        try "layout".write(
            to: sdkRoot.appendingPathComponent("skins/pixel_9/layout"),
            atomically: true,
            encoding: .utf8
        )

        let manager = EmulatorManager(
            runner: MockRunner(),
            fileManager: FileManager(),
            sdkPath: sdkRoot.path
        )
        let model = CreateAVDWizardModel(manager: manager)

        model.selectDeviceType(.desktop)
        #expect(!model.selection.showDeviceFrame)

        model.selectDeviceType(.phone)
        #expect(model.selection.showDeviceFrame)
    }

    @Test @MainActor
    func defaultsDeviceFrameBackOnWhenSwitchingToSupportedProfile() throws {
        let sdkRoot = try temporarySDKRootForModels()
        defer { try? FileManager().removeItem(at: sdkRoot) }
        try createSDKToolchainFixtureForModels(at: sdkRoot)
        try FileManager().createDirectory(
            at: sdkRoot.appendingPathComponent("skins/automotive_large_portrait"),
            withIntermediateDirectories: true
        )
        try "layout".write(
            to: sdkRoot.appendingPathComponent("skins/automotive_large_portrait/layout"),
            atomically: true,
            encoding: .utf8
        )

        let manager = EmulatorManager(
            runner: MockRunner(),
            fileManager: FileManager(),
            sdkPath: sdkRoot.path
        )
        let model = CreateAVDWizardModel(manager: manager)

        model.selectDeviceType(.automotive)
        #expect(!model.selection.showDeviceFrame)

        model.updateDeviceProfile(.init(id: "automotive_large_portrait", name: "Large Portrait"))
        #expect(model.selection.showDeviceFrame)
    }

    @Test @MainActor
    func preservesDisabledDeviceFramePreferenceWhenSwitchingThroughUnsupportedFormFactor() throws {
        let sdkRoot = try temporarySDKRootForModels()
        defer { try? FileManager().removeItem(at: sdkRoot) }
        try createSDKToolchainFixtureForModels(at: sdkRoot)
        try FileManager().createDirectory(
            at: sdkRoot.appendingPathComponent("skins/pixel_9"),
            withIntermediateDirectories: true
        )
        try "layout".write(
            to: sdkRoot.appendingPathComponent("skins/pixel_9/layout"),
            atomically: true,
            encoding: .utf8
        )

        let manager = EmulatorManager(
            runner: MockRunner(),
            fileManager: FileManager(),
            sdkPath: sdkRoot.path
        )
        let model = CreateAVDWizardModel(manager: manager)

        model.updateShowDeviceFrame(false)
        model.selectDeviceType(.desktop)
        #expect(!model.selection.showDeviceFrame)

        model.selectDeviceType(.phone)
        #expect(!model.selection.showDeviceFrame)
    }

    @Test @MainActor
    func preservesDisabledDeviceFramePreferenceWhenSwitchingThroughUnsupportedProfile() throws {
        let sdkRoot = try temporarySDKRootForModels()
        defer { try? FileManager().removeItem(at: sdkRoot) }
        try createSDKToolchainFixtureForModels(at: sdkRoot)
        try FileManager().createDirectory(
            at: sdkRoot.appendingPathComponent("skins/automotive_large_portrait"),
            withIntermediateDirectories: true
        )
        try "layout".write(
            to: sdkRoot.appendingPathComponent("skins/automotive_large_portrait/layout"),
            atomically: true,
            encoding: .utf8
        )

        let manager = EmulatorManager(
            runner: MockRunner(),
            fileManager: FileManager(),
            sdkPath: sdkRoot.path
        )
        let model = CreateAVDWizardModel(manager: manager)

        model.selectDeviceType(.automotive)
        model.updateDeviceProfile(.init(id: "automotive_large_portrait", name: "Large Portrait"))
        model.updateShowDeviceFrame(false)

        model.updateDeviceProfile(.init(id: "automotive_portrait", name: "Portrait"))
        #expect(!model.selection.showDeviceFrame)

        model.updateDeviceProfile(.init(id: "automotive_large_portrait", name: "Large Portrait"))
        #expect(!model.selection.showDeviceFrame)
    }
}

private func temporarySDKRootForModels() throws -> URL {
    let root = FileManager.default.temporaryDirectory
        .appendingPathComponent(UUID().uuidString)
    try FileManager.default.createDirectory(at: root, withIntermediateDirectories: true)
    return root
}

private func createSDKToolchainFixtureForModels(at sdkRoot: URL) throws {
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
}
