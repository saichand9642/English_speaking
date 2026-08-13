import Testing
@testable import SpeakCore

/// The verifier is the app's guarantee that it never invents mistakes, so these
/// tests are about what it *rejects* at least as much as what it keeps.
@Suite("Correction verifier")
struct CorrectionVerifierTests {

    private func correction(
        _ wrong: String,
        _ right: String,
        why: String = "Because.",
        category: MistakeCategory = .tense
    ) -> Correction {
        Correction(wrong: wrong, right: right, explanation: why, category: category)
    }

    @Test("keeps a correction whose fragment really appears")
    func keepsRealCorrection() {
        let result = CorrectionVerifier.verify(
            transcript: "Yesterday I go to the market",
            corrections: [correction("I go", "I went")]
        )
        #expect(result.count == 1)
        #expect(result.first?.wrong == "I go")
    }

    @Test("rejects a fragment the speaker never said")
    func rejectsHallucination() {
        // The classic small-model failure: a plausible error, but not this
        // learner's error. It must never reach the screen.
        let result = CorrectionVerifier.verify(
            transcript: "Yesterday I went to the market",
            corrections: [correction("I goes", "I went")]
        )
        #expect(result.isEmpty)
    }

    @Test("rejects a correction that changes nothing")
    func rejectsNoOp() {
        let result = CorrectionVerifier.verify(
            transcript: "I went to the market",
            corrections: [correction("went", "Went")]
        )
        #expect(result.isEmpty)
    }

    @Test("ignores case and punctuation when matching")
    func ignoresCaseAndPunctuation() {
        let result = CorrectionVerifier.verify(
            transcript: "Yesterday, I GO to the market.",
            corrections: [correction("i go", "I went")]
        )
        #expect(result.count == 1)
    }

    @Test("requires the fragment to be contiguous")
    func requiresContiguousRun() {
        // "I market" appears only as scattered words, not as a phrase, so a
        // correction quoting it is not describing something that was said.
        let result = CorrectionVerifier.verify(
            transcript: "I went to the market",
            corrections: [correction("I market", "I go to market")]
        )
        #expect(result.isEmpty)
    }

    @Test("drops corrections with an empty explanation")
    func dropsBlankExplanation() {
        let result = CorrectionVerifier.verify(
            transcript: "Yesterday I go home",
            corrections: [correction("I go", "I went", why: "   ")]
        )
        #expect(result.isEmpty)
    }

    @Test("deduplicates the same change reported twice")
    func deduplicates() {
        let result = CorrectionVerifier.verify(
            transcript: "Yesterday I go home",
            corrections: [
                correction("I go", "I went"),
                correction("i  go", "I went", why: "Same fix, different wording.")
            ]
        )
        #expect(result.count == 1)
    }

    @Test("rejects an implausibly long fragment")
    func rejectsLongFragment() {
        let sentence = (1...30).map { "word\($0)" }.joined(separator: " ")
        let result = CorrectionVerifier.verify(
            transcript: sentence,
            corrections: [correction(sentence, "something else")]
        )
        #expect(result.isEmpty)
    }

    @Test("empty transcript yields nothing")
    func emptyTranscript() {
        #expect(CorrectionVerifier.verify(
            transcript: "",
            corrections: [correction("a", "b")]
        ).isEmpty)
    }

    @Test("unknown category falls back to other")
    func categoryFallback() {
        #expect(MistakeCategory.from("gerundive mood") == .other)
        #expect(MistakeCategory.from("preposition") == .preposition)
        #expect(MistakeCategory.from("word_order") == .wordOrder)
        #expect(MistakeCategory.from("verb tense") == .tense)
        #expect(MistakeCategory.from(nil) == .other)
    }
}
