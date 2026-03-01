import AppKit
import Combine
import Sparkle
import SwiftUI

@MainActor
final class AppUpdater: ObservableObject {
    @Published private(set) var canCheckForUpdates = false
    @Published private(set) var installationSource: AppInstallationSource

    private let updaterController: SPUStandardUpdaterController?
    private var canCheckObservation: NSKeyValueObservation?

    init(installationSource: AppInstallationSource = .detect()) {
        self.installationSource = installationSource

        switch installationSource {
        case .direct:
            let controller = SPUStandardUpdaterController(
                startingUpdater: true,
                updaterDelegate: nil,
                userDriverDelegate: nil
            )
            updaterController = controller
            canCheckForUpdates = controller.updater.canCheckForUpdates
            canCheckObservation = controller.updater.observe(\.canCheckForUpdates, options: [.initial, .new]) { [weak self] updater, _ in
                Task { @MainActor in
                    self?.canCheckForUpdates = updater.canCheckForUpdates
                }
            }

        case .homebrew:
            updaterController = nil
            canCheckForUpdates = true
        }
    }

    var primaryUpdateActionTitle: String {
        switch installationSource {
        case .direct:
            return "Check for Updates…"
        case .homebrew:
            return AppInstallationSource.homebrewUpgradeCommand
        }
    }

    var infoButtonTitle: String {
        switch installationSource {
        case .direct:
            return "Check for Updates"
        case .homebrew:
            return AppInstallationSource.homebrewUpgradeCommand
        }
    }

    var updateActionHint: String? {
        switch installationSource {
        case .direct:
            return nil
        case .homebrew:
            return "Installed via Homebrew. Copy this command and run it in Terminal to update:"
        }
    }

    func performPrimaryUpdateAction() {
        switch installationSource {
        case .direct:
            updaterController?.checkForUpdates(nil)
        case .homebrew:
            let pasteboard = NSPasteboard.general
            pasteboard.clearContents()
            pasteboard.setString(AppInstallationSource.homebrewUpgradeCommand, forType: .string)
        }
    }
}
