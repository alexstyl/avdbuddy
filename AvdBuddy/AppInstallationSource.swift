import Foundation

enum AppInstallationSource: Equatable {
    case direct
    case homebrew

    static let homebrewCaskName = "avdbuddy"
    static let homebrewUpgradeCommand = "brew upgrade --cask avdbuddy"

    static func detect(
        bundleURL: URL = Bundle.main.bundleURL,
        fileExists: (String) -> Bool = FileManager.default.fileExists(atPath:)
    ) -> AppInstallationSource {
        guard isInstalledInApplications(bundleURL: bundleURL) else {
            return .direct
        }

        let receiptExists = homebrewReceiptDirectories.contains { fileExists($0) }
        return receiptExists ? .homebrew : .direct
    }

    private static var homebrewReceiptDirectories: [String] {
        [
            "/opt/homebrew/Caskroom/\(homebrewCaskName)",
            "/usr/local/Caskroom/\(homebrewCaskName)",
        ]
    }

    private static func isInstalledInApplications(bundleURL: URL) -> Bool {
        bundleURL.standardizedFileURL.path == "/Applications/AvdBuddy.app"
    }
}
