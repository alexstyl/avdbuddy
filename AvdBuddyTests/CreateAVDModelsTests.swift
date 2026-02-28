import Foundation
import Testing
@testable import AvdBuddy

struct CreateAVDModelsTests {
    @Test
    func generatedSuggestedNamesAreEmulatorSafe() {
        for deviceType in CreateAVDDeviceType.allCases {
            let name = deviceType.randomSuggestedName()
            let parts = name.split(separator: "_")

            #expect(!name.isEmpty)
            #expect(parts.count == 2)
            #expect(name.range(of: #"^[A-Za-z0-9._-]+$"#, options: .regularExpression) != nil)
        }
    }
}
