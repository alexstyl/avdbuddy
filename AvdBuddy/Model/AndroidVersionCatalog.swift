import Foundation

enum AndroidVersionCatalog {
    static func displayName(forAPI apiLevel: Int) -> String {
        switch apiLevel {
        case 35: return "Android 15"
        case 34: return "Android 14"
        case 33: return "Android 13"
        case 32: return "Android 12L"
        case 31: return "Android 12"
        case 30: return "Android 11"
        case 29: return "Android 10"
        case 28: return "Android 9 Pie"
        case 27: return "Android 8.1 Oreo"
        case 26: return "Android 8.0 Oreo"
        case 25: return "Android 7.1 Nougat"
        case 24: return "Android 7.0 Nougat"
        case 23: return "Android 6.0 Marshmallow"
        case 22: return "Android 5.1 Lollipop"
        case 21: return "Android 5.0 Lollipop"
        case 19: return "Android 4.4 KitKat"
        case 18: return "Android 4.3 Jelly Bean"
        case 17: return "Android 4.2 Jelly Bean"
        case 16: return "Android 4.1 Jelly Bean"
        case 15: return "Android 4.0.3 Ice Cream Sandwich"
        case 14: return "Android 4.0 Ice Cream Sandwich"
        default: return "Android"
        }
    }

    static func displayName(forIdentifier identifier: String) -> String {
        if let apiLevel = apiLevel(fromIdentifier: identifier) {
            return displayName(forAPI: apiLevel)
        }

        if identifier.hasPrefix("android-") {
            let suffix = String(identifier.dropFirst("android-".count))
            return "Android \(suffix)"
        }

        return "Android \(identifier)"
    }

    static func apiLevel(fromIdentifier identifier: String) -> Int? {
        if identifier.hasPrefix("android-") {
            let suffix = String(identifier.dropFirst("android-".count))
            let numericPrefix = suffix.prefix { $0.isNumber }
            if let apiLevel = Int(numericPrefix) {
                return apiLevel
            }
        } else if let apiLevel = Int(identifier) {
            return apiLevel
        }

        return nil
    }
}
