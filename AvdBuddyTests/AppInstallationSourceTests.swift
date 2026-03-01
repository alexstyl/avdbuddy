import Foundation
import Testing
@testable import AvdBuddy

struct AppInstallationSourceTests {
    @Test
    func detectsHomebrewInstallFromAppleSiliconCaskroomReceipt() {
        let source = AppInstallationSource.detect(
            bundleURL: URL(fileURLWithPath: "/Applications/AvdBuddy.app"),
            fileExists: { path in
                path == "/opt/homebrew/Caskroom/avdbuddy"
            }
        )

        #expect(source == .homebrew)
    }

    @Test
    func detectsHomebrewInstallFromIntelCaskroomReceipt() {
        let source = AppInstallationSource.detect(
            bundleURL: URL(fileURLWithPath: "/Applications/AvdBuddy.app"),
            fileExists: { path in
                path == "/usr/local/Caskroom/avdbuddy"
            }
        )

        #expect(source == .homebrew)
    }

    @Test
    func ignoresHomebrewReceiptsForNonApplicationsBuilds() {
        let source = AppInstallationSource.detect(
            bundleURL: URL(fileURLWithPath: "/tmp/DerivedData/Build/Products/Debug/AvdBuddy.app"),
            fileExists: { path in
                path == "/opt/homebrew/Caskroom/avdbuddy"
            }
        )

        #expect(source == .direct)
    }

    @Test
    func treatsApplicationsInstallWithoutReceiptAsDirectDownload() {
        let source = AppInstallationSource.detect(
            bundleURL: URL(fileURLWithPath: "/Applications/AvdBuddy.app"),
            fileExists: { _ in false }
        )

        #expect(source == .direct)
    }
}
