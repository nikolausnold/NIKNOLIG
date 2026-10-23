import Foundation

public enum ResponseStyle: String, CaseIterable, Codable, Identifiable, Sendable {
    case concise
    case stepByStep = "step_by_step"
    case detailed

    public var id: String { rawValue }

    public var title: String {
        switch self {
        case .concise: "Kurz"
        case .stepByStep: "Schritt für Schritt"
        case .detailed: "Ausführlich"
        }
    }
}

public struct LearningProfile: Codable, Equatable, Sendable {
    public var responseStyle: ResponseStyle
    public var feedbackEnabled: Bool
    public private(set) var helpfulCount: Int
    public private(set) var unclearCount: Int
    public private(set) var correctionCount: Int
    public private(set) var sessionSummary: String

    public init(
        responseStyle: ResponseStyle = .concise,
        feedbackEnabled: Bool = false,
        helpfulCount: Int = 0,
        unclearCount: Int = 0,
        correctionCount: Int = 0,
        sessionSummary: String = ""
    ) {
        self.responseStyle = responseStyle
        self.feedbackEnabled = feedbackEnabled
        self.helpfulCount = max(0, helpfulCount)
        self.unclearCount = max(0, unclearCount)
        self.correctionCount = max(0, correctionCount)
        self.sessionSummary = String(sessionSummary.prefix(500))
    }

    public mutating func recordHelpfulFeedback() {
        guard feedbackEnabled, helpfulCount < Int.max else { return }
        helpfulCount += 1
    }

    public mutating func recordUnclearFeedback() {
        guard feedbackEnabled, unclearCount < Int.max else { return }
        unclearCount += 1
    }

    public mutating func recordCorrectionFeedback() {
        guard feedbackEnabled, correctionCount < Int.max else { return }
        correctionCount += 1
    }

    public mutating func saveSessionSummary(_ value: String) {
        sessionSummary = String(
            value.trimmingCharacters(in: .whitespacesAndNewlines).prefix(500)
        )
    }
}

public final class LocalLearningProfileStore {
    private let fileURL: URL

    public init(directory: URL? = nil) {
        let storageDirectory = directory ?? Self.defaultDirectory()
        fileURL = storageDirectory.appendingPathComponent("learning-profile.json")
    }

    public func load() -> LearningProfile {
        guard let data = try? Data(contentsOf: fileURL),
              let profile = try? JSONDecoder().decode(LearningProfile.self, from: data) else {
            return LearningProfile()
        }
        return LearningProfile(
            responseStyle: profile.responseStyle,
            feedbackEnabled: profile.feedbackEnabled,
            helpfulCount: profile.helpfulCount,
            unclearCount: profile.unclearCount,
            correctionCount: profile.correctionCount,
            sessionSummary: profile.sessionSummary
        )
    }

    public func save(_ profile: LearningProfile) throws {
        let directory = fileURL.deletingLastPathComponent()
        try FileManager.default.createDirectory(
            at: directory,
            withIntermediateDirectories: true,
            attributes: Self.protectionAttributes
        )
        try JSONEncoder().encode(profile).write(to: fileURL, options: .atomic)
        #if os(iOS)
        do {
            try FileManager.default.setAttributes(
                Self.protectionAttributes,
                ofItemAtPath: fileURL.path
            )
        } catch {
            try? FileManager.default.removeItem(at: fileURL)
            throw error
        }
        #endif
    }

    public func clear() throws {
        guard FileManager.default.fileExists(atPath: fileURL.path) else { return }
        try FileManager.default.removeItem(at: fileURL)
    }

    private static func defaultDirectory() -> URL {
        let supportDirectory = FileManager.default.urls(
            for: .applicationSupportDirectory,
            in: .userDomainMask
        ).first ?? URL(fileURLWithPath: NSHomeDirectory(), isDirectory: true)
        return supportDirectory.appendingPathComponent("NIKNOLIG", isDirectory: true)
    }

    private static var protectionAttributes: [FileAttributeKey: Any] {
        #if os(iOS)
        [.protectionKey: FileProtectionType.completeUntilFirstUserAuthentication]
        #else
        [:]
        #endif
    }
}
