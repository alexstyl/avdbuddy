import Foundation

extension EmulatorInstance {
    var finderURL: URL? {
        let fileManager = FileManager()
        let avdRoot = fileManager.homeDirectoryForCurrentUser
            .appendingPathComponent(".android")
            .appendingPathComponent("avd")

        let directoryURL = avdRoot.appendingPathComponent("\(name).avd")
        if fileManager.fileExists(atPath: directoryURL.path) {
            return directoryURL
        }

        let iniURL = avdRoot.appendingPathComponent("\(name).ini")
        if fileManager.fileExists(atPath: iniURL.path) {
            return iniURL
        }

        return nil
    }
}
