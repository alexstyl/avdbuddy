import AppKit
import Testing
@testable import AvdBuddy

@MainActor
struct AppUpdaterTests {
    @Test
    func homebrewInstallExposesUpgradeCommandInsteadOfSparkleUpdate() {
        let updater = AppUpdater(installationSource: .homebrew)

        #expect(updater.primaryUpdateActionTitle == AppInstallationSource.homebrewUpgradeCommand)
        #expect(updater.infoButtonTitle == AppInstallationSource.homebrewUpgradeCommand)
        #expect(updater.updateActionHint == "Installed via Homebrew. Copy this command and run it in Terminal to update:")
        #expect(updater.canCheckForUpdates)
    }

    @Test
    func homebrewInstallCopiesUpgradeCommandToPasteboard() {
        let updater = AppUpdater(installationSource: .homebrew)
        let pasteboard = NSPasteboard.general
        pasteboard.clearContents()

        updater.performPrimaryUpdateAction()

        #expect(pasteboard.string(forType: .string) == AppInstallationSource.homebrewUpgradeCommand)
    }
}
