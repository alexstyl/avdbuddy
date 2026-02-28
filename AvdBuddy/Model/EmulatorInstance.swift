import Foundation

struct EmulatorInstance: Identifiable, Equatable {
    let id: String
    let name: String
    let apiLevel: Int?
    let deviceType: EmulatorDeviceType
    let colorSeed: String

    init(
        id: String,
        name: String,
        apiLevel: Int?,
        deviceType: EmulatorDeviceType = .unknown,
        colorSeed: String? = nil
    ) {
        self.id = id
        self.name = name
        self.apiLevel = apiLevel
        self.deviceType = deviceType
        self.colorSeed = colorSeed ?? Self.fallbackColorSeed(for: name)
    }

    var detailText: String {
        guard let apiLevel else { return "Unknown Android version • API ?" }
        return "\(AndroidVersionCatalog.displayName(forAPI: apiLevel)) • API \(apiLevel)"
    }

    static func fallbackColorSeed(for name: String) -> String {
        String(stableHash(for: name), radix: 16, uppercase: false)
    }

    private static func stableHash(for string: String) -> UInt64 {
        var hash: UInt64 = 1_469_598_103_934_665_603
        for byte in string.utf8 {
            hash ^= UInt64(byte)
            hash &*= 1_099_511_628_211
        }
        return hash
    }
}
