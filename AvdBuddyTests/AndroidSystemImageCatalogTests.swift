import Foundation
import Testing
@testable import AvdBuddy

struct AndroidSystemImageCatalogTests {
    @Test
    func groupsVersionFamiliesByDeviceTypeAndSortsNewestFirst() {
        let images = AndroidSystemImageCatalog.parse(from: sdkManagerCatalogFixture)

        let handheldFamilies = AndroidSystemImageCatalog.versionFamilies(from: images, for: .phone)
        let tvFamilies = AndroidSystemImageCatalog.versionFamilies(from: images, for: .tv)

        #expect(handheldFamilies.first?.id == "api-36")
        #expect(handheldFamilies.contains(where: { $0.id == "api-35" }))
        #expect(tvFamilies.count == 1)
        #expect(tvFamilies.first?.id == "api-34")
    }

    @Test
    func resolvesProductSelectionsToConcretePackage() {
        let images = AndroidSystemImageCatalog.parse(from: sdkManagerCatalogFixture)
        var selection = CreateAVDSelection()
        selection.deviceType = .phone
        selection.avdName = "Pixel_36"
        selection.selectedVersionIdentifier = "android-36"
        selection.googleServices = .googlePlay
        selection.architecture = "arm64"
        selection.deviceProfile = .init(id: "pixel_9", name: "Pixel 9")
        selection.ramPreset = .gb4
        selection.storagePreset = .gb32
        selection.sdCardPreset = .gb4

        let resolved = AndroidSystemImageCatalog.resolve(selection: selection, images: images)

        #expect(resolved?.packagePath == "system-images;android-36;google_apis_playstore;arm64-v8a")
        #expect(resolved?.deviceProfileID == "pixel_9")
        #expect(resolved?.ramMB == 4096)
        #expect(resolved?.storage == "32GB")
        #expect(resolved?.sdCard == "4096M")
    }

    @Test
    func prefersInstalledTvVariantForDefaults() {
        let images = AndroidSystemImageCatalog.parse(from: sdkManagerCatalogFixture)
        let tvRelease = AndroidSystemImageCatalog
            .versionFamilies(from: images, for: .tv)
            .first?
            .releases
            .first

        let preferredServices = AndroidSystemImageCatalog.preferredGoogleServicesOption(
            for: tvRelease,
            deviceType: .tv
        )
        let preferredArchitecture = AndroidSystemImageCatalog.preferredArchitecture(
            for: tvRelease,
            deviceType: .tv,
            googleServices: preferredServices ?? .none
        )

        #expect(preferredServices == GoogleServicesOption.none)
        #expect(preferredArchitecture == "arm64")
    }
}

private let sdkManagerCatalogFixture = """
Installed packages:
  Path                                                                               | Version | Description                                                     | Location
  system-images;android-35;google_apis;arm64-v8a                                     | 9       | Google APIs ARM 64 v8a System Image                             | system-images/android-35/google_apis/arm64-v8a

Available Packages:
  Path                                                                               | Version | Description                                                     | Location
  system-images;android-35;google_apis;arm64-v8a                                     | 9       | Google APIs ARM 64 v8a System Image                             | system-images/android-35/google_apis/arm64-v8a
  system-images;android-35;google_apis_playstore;x86_64                              | 9       | Google Play Intel x86_64 Atom System Image                      | system-images/android-35/google_apis_playstore/x86_64
  system-images;android-36;google_apis;arm64-v8a                                     | 7       | Google APIs ARM 64 v8a System Image                             | system-images/android-36/google_apis/arm64-v8a
  system-images;android-36;google_apis_playstore;arm64-v8a                           | 7       | Google Play ARM 64 v8a System Image                             | system-images/android-36/google_apis_playstore/arm64-v8a
  system-images;android-34;android-tv;arm64-v8a                                      | 3       | Android TV ARM 64 v8a System Image                              | system-images/android-34/android-tv/arm64-v8a
"""
