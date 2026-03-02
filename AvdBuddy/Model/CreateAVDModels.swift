import Foundation

enum CreateAVDDeviceType: String, CaseIterable, Identifiable {
    case phone = "Phone"
    case foldable = "Foldable"
    case tablet = "Tablet"
    case wearOS = "Wear OS"
    case desktop = "Desktop"
    case tv = "TV"
    case automotive = "Automotive"
    case xr = "XR"

    var id: String { rawValue }

    var symbolName: String {
        switch self {
        case .phone: "iphone"
        case .foldable: "rectangle.split.2x1"
        case .tablet: "ipad.landscape"
        case .wearOS: "applewatch"
        case .desktop: "desktopcomputer"
        case .tv: "tv"
        case .automotive: "car"
        case .xr: "visionpro"
        }
    }

    func randomSuggestedName() -> String {
        let first = Self.nameFirstWords.randomElement() ?? "Nova"
        var second = Self.nameSecondWords.randomElement() ?? "Harbor"
        if second == first {
            second = Self.nameSecondWords.first(where: { $0 != first }) ?? "Harbor"
        }
        return "\(first)_\(second)"
    }

    var profileOptions: [AVDDeviceProfile] {
        switch self {
        case .phone:
            return [
                .init(id: "pixel_9", name: "Pixel 9"),
                .init(id: "pixel_9a", name: "Pixel 9a"),
                .init(id: "pixel_9_pro", name: "Pixel 9 Pro"),
                .init(id: "pixel_9_pro_xl", name: "Pixel 9 Pro XL")
            ]
        case .foldable:
            return [
                .init(id: "pixel_9_pro_fold", name: "Pixel 9 Pro Fold"),
                .init(id: "pixel_fold", name: "Pixel Fold")
            ]
        case .tablet:
            return [
                .init(id: "pixel_tablet", name: "Pixel Tablet")
            ]
        case .wearOS:
            return [
                .init(id: "wearos_large_round", name: "Large Round"),
                .init(id: "wearos_rect", name: "Rectangular"),
                .init(id: "wearos_square", name: "Square")
            ]
        case .desktop:
            return [
                .init(id: "desktop_small", name: "Small Desktop"),
                .init(id: "desktop_medium", name: "Medium Desktop"),
                .init(id: "desktop_large", name: "Large Desktop")
            ]
        case .tv:
            return [
                .init(id: "tv_1080p", name: "TV 1080p"),
                .init(id: "tv_4k", name: "TV 4K"),
                .init(id: "tv_720p", name: "TV 720p")
            ]
        case .automotive:
            return [
                .init(id: "automotive_1080p_landscape", name: "1080p Landscape"),
                .init(id: "automotive_1024p_landscape", name: "1024p Landscape"),
                .init(id: "automotive_1408p_landscape_with_google_apis", name: "1408p Landscape"),
                .init(id: "automotive_distant_display", name: "Distant Display"),
                .init(id: "automotive_large_portrait", name: "Large Portrait"),
                .init(id: "automotive_portrait", name: "Portrait"),
                .init(id: "automotive_ultrawide", name: "Ultrawide")
            ]
        case .xr:
            return [
                .init(id: "xr_headset_device", name: "XR Headset"),
                .init(id: "xr_glasses_device", name: "XR Glasses")
            ]
        }
    }
}

private extension CreateAVDDeviceType {
    static let nameFirstWords = [
        "Amber",
        "Atlas",
        "Axiom",
        "Cinder",
        "Cobalt",
        "Comet",
        "Cosmic",
        "Drift",
        "Ember",
        "Fable",
        "Flare",
        "Glacier",
        "Halo",
        "Indigo",
        "Ion",
        "Juniper",
        "Lumen",
        "Mist",
        "Nova",
        "Orbit",
        "Quartz",
        "Rocket",
        "Solar",
        "Sprout",
        "Velvet"
    ]

    static let nameSecondWords = [
        "Bloom",
        "Brook",
        "Cloud",
        "Cove",
        "Dawn",
        "Echo",
        "Field",
        "Flare",
        "Grove",
        "Harbor",
        "Horizon",
        "Meadow",
        "Moon",
        "Pine",
        "Ripple",
        "River",
        "Shadow",
        "Sky",
        "Spring",
        "Star",
        "Stone",
        "Trail",
        "Vale",
        "Wave",
        "Wind"
    ]
}

struct AVDDeviceProfile: Identifiable, Equatable, Hashable {
    let id: String
    let name: String
    var fallbackToDefault: Bool = false
}

enum GoogleServicesOption: String, CaseIterable, Identifiable {
    case none = "No Google services"
    case googleAPIs = "Google APIs"
    case googlePlay = "Google Play"

    var id: String { rawValue }
}

enum RAMPreset: String, CaseIterable, Identifiable {
    case recommended = "Default"
    case gb2 = "2 GB"
    case gb4 = "4 GB"
    case gb8 = "8 GB"

    var id: String { rawValue }

    var megabytes: Int? {
        switch self {
        case .recommended: nil
        case .gb2: 2048
        case .gb4: 4096
        case .gb8: 8192
        }
    }
}

enum StoragePreset: String, CaseIterable, Identifiable {
    case gb8 = "8 GB"
    case gb16 = "16 GB"
    case gb32 = "32 GB"
    case gb64 = "64 GB"

    var id: String { rawValue }

    var configValue: String {
        rawValue.replacingOccurrences(of: " ", with: "")
    }
}

enum SDCardPreset: String, CaseIterable, Identifiable {
    case none = "None"
    case gb2 = "2 GB"
    case gb4 = "4 GB"
    case gb8 = "8 GB"

    var id: String { rawValue }

    var avdManagerValue: String? {
        switch self {
        case .none: nil
        case .gb2: "2048M"
        case .gb4: "4096M"
        case .gb8: "8192M"
        }
    }
}

struct AndroidSystemImage: Identifiable, Equatable {
    let packagePath: String
    let versionIdentifier: String
    let tag: String
    let abi: String
    let description: String
    let isInstalled: Bool

    var id: String { packagePath }

    var deviceCompatibility: Set<CreateAVDDeviceType> {
        switch normalizedTag {
        case .androidTV, .googleTV:
            return [.tv]
        case .wear:
            return [.wearOS]
        case .desktop:
            return [.desktop]
        case .automotive, .automotivePlay:
            return [.automotive]
        case .xr:
            return [.xr]
        default:
            return [.phone, .foldable, .tablet]
        }
    }

    var normalizedTag: SystemImageTag {
        SystemImageTag(rawTag: tag)
    }

    var googleServicesOption: GoogleServicesOption? {
        switch normalizedTag {
        case .default, .desktop:
            return GoogleServicesOption.none
        case .googleAPIs:
            return .googleAPIs
        case .googlePlay:
            return .googlePlay
        case .androidTV:
            return GoogleServicesOption.none
        case .googleTV:
            return .googlePlay
        case .wear:
            return GoogleServicesOption.none
        case .automotive:
            return .googleAPIs
        case .automotivePlay, .xr:
            return .googlePlay
        case .unsupported:
            return nil
        }
    }

    var isPreview: Bool {
        versionIdentifier.contains(where: \.isLetter)
    }

    var versionDisplayName: String {
        AndroidVersionCatalog.displayName(forIdentifier: versionIdentifier)
    }

    var architectureDisplayName: String {
        switch abi {
        case "arm64-v8a": "arm64"
        case "x86_64": "x86_64"
        case "x86": "x86"
        case "armeabi-v7a": "armv7"
        default: abi
        }
    }
}

enum SystemImageTag: Equatable {
    case `default`
    case googleAPIs
    case googlePlay
    case androidTV
    case googleTV
    case wear
    case desktop
    case automotive
    case automotivePlay
    case xr
    case unsupported

    init(rawTag: String) {
        if rawTag == "default" {
            self = .default
        } else if rawTag.hasPrefix("google_apis_playstore") {
            self = .googlePlay
        } else if rawTag == "google_apis" {
            self = .googleAPIs
        } else if rawTag == "android-tv" {
            self = .androidTV
        } else if rawTag == "google-tv" {
            self = .googleTV
        } else if rawTag.contains("wear") {
            self = .wear
        } else if rawTag == "android-desktop" {
            self = .desktop
        } else if rawTag == "android-automotive" {
            self = .automotive
        } else if rawTag == "android-automotive-playstore" || rawTag == "android-automotive-distant-display-playstore" {
            self = .automotivePlay
        } else if rawTag == "google-xr" {
            self = .xr
        } else {
            self = .unsupported
        }
    }
}

struct AndroidRelease: Identifiable, Equatable {
    let versionIdentifier: String
    let title: String
    let subtitle: String?
    let images: [AndroidSystemImage]

    var id: String { versionIdentifier }
    var isPreview: Bool { images.contains(where: \.isPreview) }
    var installedCount: Int { images.filter(\.isInstalled).count }
}

struct AndroidVersionFamily: Identifiable, Equatable {
    let id: String
    let title: String
    let subtitle: String?
    let releases: [AndroidRelease]

    var defaultReleaseIdentifier: String? { releases.first?.versionIdentifier }
}

struct CreateAVDSelection {
    var deviceType: CreateAVDDeviceType = .phone
    var avdName: String = CreateAVDDeviceType.phone.randomSuggestedName()
    var selectedVersionFamilyID: String?
    var selectedVersionIdentifier: String?
    var googleServices: GoogleServicesOption = .googlePlay
    var architecture: String?
    var deviceProfile: AVDDeviceProfile = CreateAVDDeviceType.phone.profileOptions[0]
    var ramPreset: RAMPreset = .recommended
    var storagePreset: StoragePreset = .gb16
    var sdCardPreset: SDCardPreset = .none
    var showDeviceFrame = true
}

struct CreateAVDResolvedConfiguration {
    let packagePath: String
    let avdName: String
    let deviceProfileID: String
    let ramMB: Int?
    let storage: String
    let sdCard: String?
    let showDeviceFrame: Bool
    let colorSeed: String
}
