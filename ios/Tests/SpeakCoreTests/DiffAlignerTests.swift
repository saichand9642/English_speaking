import Testing
@testable import SpeakCore

@Suite("Diff aligner")
struct DiffAlignerTests {

    private func render(_ spans: [DiffSpan]) -> String {
        spans.map { span in
            switch span.kind {
            case .unchanged: return span.text
            case .removed: return "[-\(span.text)]"
            case .added: return "[+\(span.text)]"
            }
        }.joined(separator: " ")
    }

    @Test("marks a replaced word")
    func replacedWord() {
        let spans = DiffAligner.align(
            original: "Yesterday I go home",
            corrected: "Yesterday I went home"
        )
        #expect(render(spans) == "Yesterday I [-go] [+went] home")
    }

    @Test("marks an inserted word")
    func insertedWord() {
        let spans = DiffAligner.align(
            original: "I went to market",
            corrected: "I went to the market"
        )
        #expect(render(spans) == "I went to [+the] market")
    }

    @Test("marks a deleted word")
    func deletedWord() {
        let spans = DiffAligner.align(
            original: "We discussed about it",
            corrected: "We discussed it"
        )
        #expect(render(spans) == "We discussed [-about] it")
    }

    @Test("identical sentences produce no changes")
    func identical() {
        let spans = DiffAligner.align(original: "I am fine", corrected: "I am fine")
        #expect(spans.allSatisfy { $0.kind == .unchanged })
    }

    @Test("differences in case alone are not changes")
    func caseOnly() {
        // Whisper's capitalisation is its own; flagging it as the learner's
        // mistake would be noise.
        let spans = DiffAligner.align(
            original: "yesterday i went home",
            corrected: "Yesterday I went home"
        )
        #expect(spans.allSatisfy { $0.kind == .unchanged })
        #expect(render(spans) == "Yesterday I went home")
    }

    @Test("handles a full rewrite")
    func fullRewrite() {
        let spans = DiffAligner.align(original: "me go there", corrected: "I went there")
        #expect(spans.contains { $0.kind == .removed })
        #expect(spans.contains { $0.kind == .added })
        #expect(spans.contains { $0.kind == .unchanged && $0.text == "there" })
    }

    @Test("empty original is all additions")
    func emptyOriginal() {
        let spans = DiffAligner.align(original: "", corrected: "I went home")
        #expect(spans.count == 1)
        #expect(spans.first?.kind == .added)
    }

    @Test("empty correction is all removals")
    func emptyCorrection() {
        let spans = DiffAligner.align(original: "I went home", corrected: "")
        #expect(spans.count == 1)
        #expect(spans.first?.kind == .removed)
    }

    @Test("both empty yields nothing")
    func bothEmpty() {
        #expect(DiffAligner.align(original: "", corrected: "").isEmpty)
    }

    @Test("collapses extra whitespace")
    func extraWhitespace() {
        let spans = DiffAligner.align(original: "I   went    home", corrected: "I went home")
        #expect(spans.allSatisfy { $0.kind == .unchanged })
    }

    @Test("merges adjacent changes of the same kind")
    func mergesRuns() {
        let spans = DiffAligner.align(
            original: "He is having two brother",
            corrected: "He has two brothers"
        )
        // No empty spans, and no two neighbours share a kind.
        #expect(spans.allSatisfy { !$0.text.isEmpty })
        for pair in zip(spans, spans.dropFirst()) {
            #expect(pair.0.kind != pair.1.kind)
        }
    }
}
