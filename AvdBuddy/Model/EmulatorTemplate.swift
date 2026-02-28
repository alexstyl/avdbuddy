import Foundation

enum EmulatorTemplate: String, CaseIterable, Identifiable {
    case api35 = "API 35"
    case api24 = "API 24"

    var id: String { rawValue }

    var systemImagePackage: String {
        switch self {
        case .api35:
            return "system-images;android-35;google_apis;x86_64"
        case .api24:
            return "system-images;android-24;google_apis;x86"
        }
    }

    var defaultAvdName: String {
        switch self {
        case .api35:
            return "API_35_Emulator"
        case .api24:
            return "API_24_Emulator"
        }
    }
}
