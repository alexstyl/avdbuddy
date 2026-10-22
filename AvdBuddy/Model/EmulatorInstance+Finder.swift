import Foundation

extension EmulatorInstance {
    func finderURL(in avdRoot: URL, fileManager: FileManager = FileManager()) -> URL? {

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
