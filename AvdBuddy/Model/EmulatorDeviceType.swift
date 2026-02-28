import Foundation

enum EmulatorDeviceType: Equatable {
    case phone
    case tablet
    case foldable
    case tv
    case unknown

    var label: String {
        switch self {
        case .phone: return "Phone"
        case .tablet: return "Tablet"
        case .foldable: return "Foldable"
        case .tv: return "TV"
        case .unknown: return "Unknown"
        }
    }

    var symbolName: String {
        switch self {
        case .phone: return "iphone"
        case .tablet: return "ipad"
        case .foldable: return "rectangle.split.2x1"
        case .tv: return "tv"
        case .unknown: return "questionmark.square.dashed"
        }
    }
}
