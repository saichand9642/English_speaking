import Testing
@testable import SpeakCore

/// The streaming extractor is what lets speech start before the model has finished
/// thinking, so it has to survive the JSON arriving in arbitrarily ugly fragments.
@Suite("Reply stream extractor")
struct ReplyStreamExtractorTests {

    /// Feeds text one character at a time, the worst case for a state machine.
    private func drip(_ raw: String) -> String {
        let extractor = ReplyStreamExtractor()
        return raw.reduce(into: "") { out, ch in out += extractor.accept(String(ch)) }
    }

    @Test("extracts the reply from a complete document")
    func completeDocument() {
        let raw = #"{"reply": "Good morning. What did you eat?", "corrected": "", "corrections": []}"#
        #expect(drip(raw) == "Good morning. What did you eat?")
    }

    @Test("emits text before the document is finished")
    func emitsEarly() {
        // This is the whole point: usable text while corrections are still coming.
        let extractor = ReplyStreamExtractor()
        let first = extractor.accept(#"{"reply": "That sounds good."#)
        #expect(first == "That sounds good.")
        #expect(!extractor.isComplete)
    }

    @Test("marks itself complete at the closing quote")
    func marksComplete() {
        let extractor = ReplyStreamExtractor()
        _ = extractor.accept(#"{"reply": "Done."#)
        #expect(!extractor.isComplete)
        _ = extractor.accept("\", \"corrected\"")
        #expect(extractor.isComplete)
    }

    @Test("handles the key split across fragments")
    func splitKey() {
        let extractor = ReplyStreamExtractor()
        var out = ""
        out += extractor.accept(#"{"re"#)
        out += extractor.accept(#"ply""#)
        out += extractor.accept(#": "Hello."#)
        #expect(out == "Hello.")
    }

    @Test("unescapes escaped quotes inside the reply")
    func escapedQuotes() {
        let raw = #"{"reply": "Say \"thank you\" next time.", "corrected": ""}"#
        #expect(drip(raw) == "Say \"thank you\" next time.")
    }

    @Test("unescapes newlines")
    func escapedNewline() {
        let raw = #"{"reply": "First.\nSecond.", "corrected": ""}"#
        #expect(drip(raw) == "First.\nSecond.")
    }

    @Test("an escaped backslash does not end the string")
    func escapedBackslash() {
        let raw = #"{"reply": "back\\slash", "corrected": ""}"#
        #expect(drip(raw) == "back\\slash")
    }

    @Test("ignores content after the reply closes")
    func ignoresTail() {
        let raw = #"{"reply": "Only this.", "corrected": "not this", "corrections": []}"#
        #expect(drip(raw) == "Only this.")
    }

    @Test("yields nothing when there is no reply key")
    func noReplyKey() {
        #expect(drip(#"{"corrected": "x", "corrections": []}"#).isEmpty)
    }

    @Test("reset allows reuse across turns")
    func reset() {
        let extractor = ReplyStreamExtractor()
        _ = extractor.accept(#"{"reply": "One.""#)
        #expect(extractor.isComplete)
        extractor.reset()
        #expect(!extractor.isComplete)
        #expect(extractor.accept(#"{"reply": "Two."#) == "Two.")
    }
}

@Suite("Sentence chunker")
struct SentenceChunkerTests {

    @Test("releases a sentence once it is complete")
    func releasesSentence() {
        let chunker = SentenceChunker()
        #expect(chunker.accept("That sounds").isEmpty)
        #expect(chunker.accept(" good. And then") == ["That sounds good."])
    }

    @Test("handles several sentences in one fragment")
    func severalSentences() {
        let chunker = SentenceChunker()
        #expect(chunker.accept("One. Two! Three? ") == ["One.", "Two!", "Three?"])
    }

    @Test("keeps decimals intact")
    func keepsDecimals() {
        // A full stop only ends a sentence when a space follows, otherwise speech
        // would break "3.5" into two utterances.
        let chunker = SentenceChunker()
        #expect(chunker.accept("It costs 3.5 rupees").isEmpty)
        #expect(chunker.accept(". Next") == ["It costs 3.5 rupees."])
    }

    @Test("a sentence ending at a closing quote is released whole")
    func closingQuote() {
        let chunker = SentenceChunker()
        #expect(chunker.accept("He said \"hello.\" Then") == ["He said \"hello.\""])
    }

    @Test("newline ends a sentence")
    func newlineEnds() {
        let chunker = SentenceChunker()
        #expect(chunker.accept("A line\nmore") == ["A line"])
    }

    @Test("breaks a very long run at a word boundary")
    func softLimit() {
        // Without this, a model that never emits punctuation would leave speech
        // waiting for a full stop that is not coming.
        let chunker = SentenceChunker(softLimit: 40)
        let ready = chunker.accept(String(repeating: "word ", count: 20))
        #expect(!ready.isEmpty)
        // A word is never split in half.
        for chunk in ready {
            #expect(chunk.split(separator: " ").allSatisfy { $0 == "word" })
        }
    }

    @Test("flush returns the trailing fragment")
    func flushTrailing() {
        let chunker = SentenceChunker()
        _ = chunker.accept("An unfinished thought")
        #expect(chunker.flush() == "An unfinished thought")
        #expect(chunker.flush().isEmpty)
    }

    @Test("empty input yields nothing")
    func emptyInput() {
        #expect(SentenceChunker().accept("").isEmpty)
    }
}
