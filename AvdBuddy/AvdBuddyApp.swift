import SwiftUI

@main
struct AvdBuddyApp: App {
    @StateObject private var appUpdater = AppUpdater()

    var body: some Scene {
        WindowGroup {
            ContentView()
                .environmentObject(appUpdater)
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
