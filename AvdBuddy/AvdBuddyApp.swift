import SwiftUI

@main
struct AvdBuddyApp: App {
    @StateObject private var appUpdater = AppUpdater()

    var body: some Scene {
        WindowGroup {
            if NSClassFromString("XCTestCase") == nil {
                ContentView()
                    .environmentObject(appUpdater)
            } else {
                // Unit tests supply their own managers and SDK fixtures.
                Color.clear
            }
        }
        .defaultSize(width: 980, height: 680)
        .commands {
            CommandGroup(after: .appInfo) {
                Button(appUpdater.primaryUpdateActionTitle) {
                    appUpdater.performPrimaryUpdateAction()
                }
                .disabled(!appUpdater.canCheckForUpdates)
            }
        }
    }
}
