import Foundation

/// The kinds of mistake the tutor is allowed to report.
///
/// A closed set means the progress screens can group and rank mistakes reliably,
/// and it stops the language model inventing a new category name every turn.
public enum MistakeCategory: String, Sendable, CaseIterable {
    case tense
    case article
    case preposition
    case plural
    case wordOrder
    case wordChoice
    case other

    public var label: String {
        switch self {
        case .tense: return "Tense"
        case .article: return "A / an / the"
        case .preposition: return "Preposition"
        case .plural: return "Singular / plural"
        case .wordOrder: return "Word order"
        case .wordChoice: return "Word choice"
        case .other: return "Other"
        }
    }

    /// Lenient lookup, because a small model will not always echo our spelling.
    public static func from(_ raw: String?) -> MistakeCategory {
        guard let raw else { return .other }
        let key = raw.trimmingCharacters(in: .whitespacesAndNewlines)
            .lowercased()
            .replacingOccurrences(of: " ", with: "_")

        switch key {
        case "tense": return .tense
        case "article": return .article
        case "preposition": return .preposition
        case "plural": return .plural
        case "word_order": return .wordOrder
        case "word_choice": return .wordChoice
        case "other": return .other
        default: break
        }

        if key.contains("tense") || key.contains("verb") { return .tense }
        if key.contains("article") { return .article }
        if key.contains("prep") { return .preposition }
        if key.contains("plural") || key.contains("singular") || key.contains("count") {
            return .plural
        }
        if key.contains("order") || key.contains("syntax") { return .wordOrder }
        if key.contains("choice") || key.contains("vocab") || key.contains("word") {
            return .wordChoice
        }
        return .other
    }
}

/// A single correction: the exact fragment that was wrong, the fix, and a
/// plain-language reason.
public struct Correction: Equatable, Sendable {
    public let wrong: String
    public let right: String
    public let explanation: String
    public let category: MistakeCategory

    public init(wrong: String, right: String, explanation: String, category: MistakeCategory) {
        self.wrong = wrong
        self.right = right
        self.explanation = explanation
        self.category = category
    }
}

/// Where a pronunciation observation came from. The UI states this honestly.
public enum PronunciationEvidence: Sendable {
    /// Compared against a known target sentence. Reliable.
    case targetComparison

    /// The acoustic model was unsure of this word. Suggestive, not conclusive.
    case lowConfidence
}

public struct PronunciationNote: Equatable, Sendable {
    public let word: String
    /// What the recogniser actually heard, when we can tell.
    public let heardAs: String?
    /// A physical instruction: tongue, lips, or stress.
    public let tip: String
    /// 0...1 acoustic confidence for this word.
    public let confidence: Float
    public let evidence: PronunciationEvidence

    public init(
        word: String,
        heardAs: String?,
        tip: String,
        confidence: Float,
        evidence: PronunciationEvidence
    ) {
        self.word = word
        self.heardAs = heardAs
        self.tip = tip
        self.confidence = confidence
        self.evidence = evidence
    }
}

/// How a learner's sentence differs from its corrected form, as a flat run of
/// spans. The conversation screen renders `.removed` struck through and `.added`
/// highlighted, inline, so the change is readable at a glance.
public struct DiffSpan: Equatable, Sendable {
    public enum Kind: Sendable { case unchanged, removed, added }

    public let text: String
    public let kind: Kind

    public init(text: String, kind: Kind) {
        self.text = text
        self.kind = kind
    }
}

/// Everything the tutor produced for one spoken turn.
public struct TutorFeedback: Equatable, Sendable {
    /// What the learner actually said, errors intact.
    public let transcript: String
    /// The whole sentence rewritten correctly, or nil when nothing was wrong.
    public let correctedSentence: String?
    public let corrections: [Correction]
    /// What the tutor says out loud, including a follow-up question.
    public let spokenReply: String
    public let pronunciation: [PronunciationNote]

    public init(
        transcript: String,
        correctedSentence: String?,
        corrections: [Correction],
        spokenReply: String,
        pronunciation: [PronunciationNote] = []
    ) {
        self.transcript = transcript
        self.correctedSentence = correctedSentence
        self.corrections = corrections
        self.spokenReply = spokenReply
        self.pronunciation = pronunciation
    }

    public var wasCorrect: Bool { corrections.isEmpty }
}
