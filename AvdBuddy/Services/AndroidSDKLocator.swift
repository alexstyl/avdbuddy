import Foundation

enum AndroidSDKLocator {
    static func sdkPath(environment: [String: String] = ProcessInfo.processInfo.environment) -> String {
        if let sdkRoot = environment["ANDROID_SDK_ROOT"], !sdkRoot.isEmpty {
            return sdkRoot
        }
        if let androidHome = environment["ANDROID_HOME"], !androidHome.isEmpty {
            return androidHome
        }
        let home = FileManager().homeDirectoryForCurrentUser.path
        return "\(home)/Library/Android/sdk"
    }
}
