import Foundation
import XCTest
@testable import NIKNOLIGCore

final class NIKNOLIGCoreTests: XCTestCase {
    func testBackendEndpointResolvesOnlyHTTPSOrigins() throws {
        let endpoint = try BackendEndpoint(baseURL: "https://example.org/")
        XCTAssertEqual(endpoint.healthURL.absoluteString, "https://example.org/v1/health")

        for value in [
            "http://example.org",
            "https://user@example.org",
            "https://example.org:443",
            "https://example.org/path",
            "https://example.org?query",
            "https://example.org#fragment",
            "not a URL"
        ] {
            XCTAssertThrowsError(try BackendEndpoint(baseURL: value), value)
        }
    }

    func testHealthResponseRequiresExpectedSandboxPayload() {
        let valid = Data(#"{"status":"ok","apiVersion":"v1","mode":"sandbox"}"#.utf8)
        let wrongMode = Data(#"{"status":"ok","apiVersion":"v1","mode":"production"}"#.utf8)
        let extraData = Data(#"{"status":"ok","apiVersion":"v1","mode":"sandbox","extra":true}"#.utf8)

        XCTAssertTrue(BackendHealthResponse.isValid(valid))
        XCTAssertFalse(BackendHealthResponse.isValid(wrongMode))
        XCTAssertFalse(BackendHealthResponse.isValid(extraData))
        XCTAssertFalse(BackendHealthResponse.isValid(Data("not json".utf8)))
    }

    func testLearningProfileRequiresOptInAndBoundsSavedSummary() {
        var profile = LearningProfile()
        profile.recordHelpfulFeedback()
        XCTAssertEqual(profile.helpfulCount, 0)

        profile.feedbackEnabled = true
        profile.recordHelpfulFeedback()
        profile.recordUnclearFeedback()
        profile.recordCorrectionFeedback()
        profile.saveSessionSummary("  " + String(repeating: "x", count: 510) + "  ")

        XCTAssertEqual(profile.helpfulCount, 1)
        XCTAssertEqual(profile.unclearCount, 1)
        XCTAssertEqual(profile.correctionCount, 1)
        XCTAssertEqual(profile.sessionSummary.count, 500)
    }

    func testLocalProfilePersistsAndClearsWithoutNetwork() throws {
        let directory = FileManager.default.temporaryDirectory
            .appendingPathComponent(UUID().uuidString, isDirectory: true)
        defer { try? FileManager.default.removeItem(at: directory) }
        let store = LocalLearningProfileStore(directory: directory)
        var profile = LearningProfile(responseStyle: .stepByStep, feedbackEnabled: true)
        profile.recordHelpfulFeedback()
        profile.saveSessionSummary("Nur lokal")

        try store.save(profile)
        XCTAssertEqual(store.load(), profile)
        try store.clear()
        XCTAssertEqual(store.load(), LearningProfile())
    }
}
