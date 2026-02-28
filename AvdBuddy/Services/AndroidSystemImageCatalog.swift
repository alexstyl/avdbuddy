import Foundation

struct AndroidSystemImageCatalog {
    static func parse(from sdkManagerListOutput: String) -> [AndroidSystemImage] {
        let lines = sdkManagerListOutput.split(whereSeparator: \.isNewline).map(String.init)
        var section: Section?
        var installedPackages = Set<String>()
        var availablePackages = Set<String>()
        var packageDescriptions: [String: String] = [:]

        for line in lines {
            let trimmed = line.trimmingCharacters(in: .whitespaces)
            if trimmed == "Installed packages:" {
                section = .installed
                continue
            }
            if trimmed == "Available Packages:" {
                section = .available
                continue
            }
            guard let currentSection = section else { continue }
            guard trimmed.hasPrefix("system-images;") else { continue }

            let columns = trimmed
                .split(separator: "|", omittingEmptySubsequences: false)
                .map { $0.trimmingCharacters(in: .whitespaces) }

            guard let packagePath = columns.first, packagePath.hasPrefix("system-images;") else {
                continue
            }

            let description = columns.count > 2 ? columns[2] : ""
            packageDescriptions[packagePath] = description
            switch currentSection {
            case .installed:
                installedPackages.insert(packagePath)
            case .available:
                availablePackages.insert(packagePath)
            }
        }

        let allPackages = installedPackages.union(availablePackages)
        return allPackages.compactMap { packagePath in
            parsePackage(
                packagePath,
                description: packageDescriptions[packagePath] ?? "",
                isInstalled: installedPackages.contains(packagePath)
            )
        }
    }

    static func versionFamilies(
        from images: [AndroidSystemImage],
        for deviceType: CreateAVDDeviceType
    ) -> [AndroidVersionFamily] {
        let compatibleImages = images.filter { $0.deviceCompatibility.contains(deviceType) }
        let releasesByIdentifier = Dictionary(grouping: compatibleImages, by: \.versionIdentifier)

        let releases = releasesByIdentifier.values
            .map { versions in
                let sortedImages = versions.sorted(by: imageSort)
                let versionIdentifier = versions[0].versionIdentifier
                return AndroidRelease(
                    versionIdentifier: versionIdentifier,
                    title: releaseTitle(for: versionIdentifier),
                    subtitle: releaseSubtitle(for: versionIdentifier),
                    images: sortedImages
                )
            }
            .sorted(by: releaseSort)

        let groupedFamilies = Dictionary(grouping: releases, by: familyID(for:))
        return groupedFamilies.map { familyID, releases in
            let sortedReleases = releases.sorted(by: releaseSort)
            return AndroidVersionFamily(
                id: familyID,
                title: familyTitle(for: familyID, releases: sortedReleases),
                subtitle: familySubtitle(for: familyID, releases: sortedReleases),
                releases: sortedReleases
            )
        }
        .sorted(by: familySort)
    }

    static func resolve(
        selection: CreateAVDSelection,
        images: [AndroidSystemImage]
    ) -> CreateAVDResolvedConfiguration? {
        guard let versionIdentifier = selection.selectedVersionIdentifier else { return nil }
        let matchingImages = images.filter {
            $0.versionIdentifier == versionIdentifier &&
            $0.deviceCompatibility.contains(selection.deviceType) &&
            $0.googleServicesOption == selection.googleServices
        }

        let filteredByArchitecture = matchingImages.filter { image in
            guard let architecture = selection.architecture else { return true }
            return image.architectureDisplayName == architecture
        }

        let resolvedImage = (filteredByArchitecture.isEmpty ? matchingImages : filteredByArchitecture)
            .sorted(by: imageSort)
            .first

        guard let resolvedImage else { return nil }

        let profileID = selection.deviceProfile.fallbackToDefault
            ? selection.deviceType.profileOptions[0].id
            : selection.deviceProfile.id

        return CreateAVDResolvedConfiguration(
            packagePath: resolvedImage.packagePath,
            avdName: selection.avdName,
            deviceProfileID: profileID,
            ramMB: selection.ramPreset.megabytes,
            storage: selection.storagePreset.configValue,
            sdCard: selection.sdCardPreset.avdManagerValue,
            colorSeed: EmulatorInstance.fallbackColorSeed(for: selection.avdName)
        )
    }

    static func availableGoogleServiceOptions(
        for release: AndroidRelease?,
        deviceType: CreateAVDDeviceType
    ) -> [GoogleServicesOption] {
        guard let release else { return [] }
        let options = Set(release.images.compactMap(\.googleServicesOption))

        switch deviceType {
        case .tv:
            return [.none, .googlePlay].filter { options.contains($0) }
        case .wearOS:
            return [.none].filter { options.contains($0) }
        case .phone, .tablet, .foldable:
            return [.none, .googleAPIs, .googlePlay].filter { options.contains($0) }
        }
    }

    static func availableArchitectures(
        for release: AndroidRelease?,
        deviceType: CreateAVDDeviceType,
        googleServices: GoogleServicesOption
    ) -> [String] {
        guard let release else { return [] }
        let architectures = Set(
            release.images
                .filter { $0.deviceCompatibility.contains(deviceType) && $0.googleServicesOption == googleServices }
                .map(\.architectureDisplayName)
        )

        return architectures.sorted(by: architectureSort)
    }

    static func preferredGoogleServicesOption(
        for release: AndroidRelease?,
        deviceType: CreateAVDDeviceType
    ) -> GoogleServicesOption? {
        guard let release else { return nil }

        let compatibleImages = release.images
            .filter { $0.deviceCompatibility.contains(deviceType) }
            .sorted(by: imageSort)

        if let installedOption = compatibleImages
            .first(where: { $0.isInstalled })?
            .googleServicesOption {
            return installedOption
        }

        return availableGoogleServiceOptions(for: release, deviceType: deviceType).first
    }

    static func preferredArchitecture(
        for release: AndroidRelease?,
        deviceType: CreateAVDDeviceType,
        googleServices: GoogleServicesOption
    ) -> String? {
        guard let release else { return nil }

        let matchingImages = release.images
            .filter {
                $0.deviceCompatibility.contains(deviceType) &&
                $0.googleServicesOption == googleServices
            }
            .sorted(by: imageSort)

        if let installedArchitecture = matchingImages
            .first(where: { $0.isInstalled })?
            .architectureDisplayName {
            return installedArchitecture
        }

        return matchingImages.first?.architectureDisplayName
    }

    private static func parsePackage(
        _ packagePath: String,
        description: String,
        isInstalled: Bool
    ) -> AndroidSystemImage? {
        let components = packagePath.split(separator: ";").map(String.init)
        guard components.count == 4 else { return nil }
        guard components[0] == "system-images" else { return nil }

        return AndroidSystemImage(
            packagePath: packagePath,
            versionIdentifier: components[1],
            tag: components[2],
            abi: components[3],
            description: description,
            isInstalled: isInstalled
        )
    }

    private static func releaseSubtitle(for versionIdentifier: String) -> String? {
        if let apiLevel = AndroidVersionCatalog.apiLevel(fromIdentifier: versionIdentifier) {
            if versionIdentifier.contains("-ext"), let extRange = versionIdentifier.range(of: "ext", options: .backwards) {
                let extValue = versionIdentifier[extRange.upperBound...]
                return "API \(apiLevel) Extension \(extValue)"
            }
            return "API \(apiLevel)"
        }

        if versionIdentifier.hasPrefix("android-") {
            return String(versionIdentifier.dropFirst("android-".count))
        }

        return versionIdentifier
    }

    private static func releaseTitle(for versionIdentifier: String) -> String {
        if AndroidVersionCatalog.apiLevel(fromIdentifier: versionIdentifier) != nil {
            if versionIdentifier.contains("-ext"), let extRange = versionIdentifier.range(of: "ext", options: .backwards) {
                let extValue = versionIdentifier[extRange.upperBound...]
                return "Extension \(extValue)"
            }
            if versionIdentifier.contains(".") {
                return String(versionIdentifier.dropFirst("android-".count))
            }
            return "Base release"
        }

        if versionIdentifier.hasPrefix("android-") {
            return String(versionIdentifier.dropFirst("android-".count))
        }

        return versionIdentifier
    }

    private static func familyID(for release: AndroidRelease) -> String {
        if let apiLevel = AndroidVersionCatalog.apiLevel(fromIdentifier: release.versionIdentifier) {
            return "api-\(apiLevel)"
        }
        return release.versionIdentifier
    }

    private static func familyTitle(for familyID: String, releases: [AndroidRelease]) -> String {
        if familyID.hasPrefix("api-"),
           let apiLevel = Int(familyID.dropFirst("api-".count)) {
            return AndroidVersionCatalog.displayName(forAPI: apiLevel)
        }
        return releases.first?.title ?? "Android"
    }

    private static func familySubtitle(for familyID: String, releases: [AndroidRelease]) -> String? {
        if familyID.hasPrefix("api-"),
           let apiLevel = Int(familyID.dropFirst("api-".count)) {
            return "API \(apiLevel)"
        }
        return releases.first?.subtitle
    }

    private static func familySort(lhs: AndroidVersionFamily, rhs: AndroidVersionFamily) -> Bool {
        let lhsAPI = lhs.releases.compactMap { AndroidVersionCatalog.apiLevel(fromIdentifier: $0.versionIdentifier) }.max() ?? -1
        let rhsAPI = rhs.releases.compactMap { AndroidVersionCatalog.apiLevel(fromIdentifier: $0.versionIdentifier) }.max() ?? -1
        if lhsAPI != rhsAPI {
            return lhsAPI > rhsAPI
        }
        return lhs.id.localizedStandardCompare(rhs.id) == .orderedDescending
    }

    private static func releaseSort(lhs: AndroidRelease, rhs: AndroidRelease) -> Bool {
        let lhsAPI = AndroidVersionCatalog.apiLevel(fromIdentifier: lhs.versionIdentifier) ?? -1
        let rhsAPI = AndroidVersionCatalog.apiLevel(fromIdentifier: rhs.versionIdentifier) ?? -1
        if lhsAPI != rhsAPI {
            return lhsAPI > rhsAPI
        }
        if lhs.isPreview != rhs.isPreview {
            return rhs.isPreview
        }
        return lhs.versionIdentifier.localizedStandardCompare(rhs.versionIdentifier) == .orderedDescending
    }

    private static func imageSort(lhs: AndroidSystemImage, rhs: AndroidSystemImage) -> Bool {
        if lhs.isInstalled != rhs.isInstalled {
            return lhs.isInstalled && !rhs.isInstalled
        }
        if lhs.normalizedTag != rhs.normalizedTag {
            return tagPriority(lhs.normalizedTag) < tagPriority(rhs.normalizedTag)
        }
        return architectureSort(lhs.architectureDisplayName, rhs.architectureDisplayName)
    }

    private static func tagPriority(_ tag: SystemImageTag) -> Int {
        switch tag {
        case .googlePlay: 0
        case .googleAPIs: 1
        case .default: 2
        case .googleTV: 3
        case .androidTV: 4
        case .wear: 5
        case .desktop: 6
        case .unsupported: 7
        }
    }

    private static func architectureSort(_ lhs: String, _ rhs: String) -> Bool {
        architecturePriority(lhs) < architecturePriority(rhs)
    }

    private static func architecturePriority(_ architecture: String) -> Int {
        switch architecture {
        case "arm64": 0
        case "x86_64": 1
        case "x86": 2
        case "armv7": 3
        default: 4
        }
    }

    private enum Section {
        case installed
        case available
    }
}
