import Foundation
import Testing
@testable import SpeakCore

@Suite("Practice content")
struct PracticeContentTests {

    @Test("topic rotation is stable and in range")
    func topicRotation() {
        for day in Int64(0)..<100 {
            let topic = PracticeContent.topic(forEpochDay: day)
            #expect(PracticeContent.topics.contains(topic))
        }
        // Same day always gives the same topic, so reopening the app is not a reroll.
        #expect(
            PracticeContent.topic(forEpochDay: 20_000)
                == PracticeContent.topic(forEpochDay: 20_000)
        )
        // And it does move day to day.
        #expect(
            PracticeContent.topic(forEpochDay: 20_000)
                != PracticeContent.topic(forEpochDay: 20_001)
        )
    }

    @Test("negative epoch days do not crash")
    func negativeDays() {
        // Swift's % keeps the sign, so a pre-1970 date would index out of bounds
        // without the explicit wrap.
        let topic = PracticeContent.topic(forEpochDay: -5)
        #expect(PracticeContent.topics.contains(topic))
    }

    @Test("sentence indexing wraps in both directions")
    func sentenceWrapping() {
        let count = PracticeContent.readAloudSentences.count
        #expect(PracticeContent.sentence(at: 0) == PracticeContent.sentence(at: count))
        #expect(PracticeContent.sentence(at: -1) == PracticeContent.sentence(at: count - 1))
    }

    @Test("every topic and sentence is usable")
    func contentIsWellFormed() {
        for topic in PracticeContent.topics {
            #expect(!topic.title.isEmpty)
            #expect(topic.opener.hasSuffix("?") || topic.opener.hasSuffix("."))
        }
        for sentence in PracticeContent.readAloudSentences {
            #expect(!sentence.focus.isEmpty)
            #expect(sentence.text.split(separator: " ").count >= 5)
        }
    }
}

@Suite("Tutor prompt")
struct TutorPromptTests {

    @Test("uses Gemma turn markers and ends ready for the model")
    func gemmaFormat() {
        let prompt = TutorPrompt.build(
            topic: "Food", history: [], studentSentence: "Yesterday I go to market"
        )
        #expect(prompt.contains("<start_of_turn>user"))
        #expect(prompt.contains("<end_of_turn>"))
        // Must end on an open model turn so generation begins immediately.
        #expect(prompt.hasSuffix("<start_of_turn>model\n"))
        // No <bos>: tokenisation adds it, and a duplicate degrades output.
        #expect(!prompt.contains("<bos>"))
    }

    @Test("includes the topic and the sentence")
    func includesContext() {
        let prompt = TutorPrompt.build(
            topic: "Food", history: [], studentSentence: "I eat rice"
        )
        #expect(prompt.contains("Topic: Food"))
        #expect(prompt.contains("I eat rice"))
    }

    @Test("bounds history to keep prefill small")
    func boundsHistory() {
        let history = (1...10).map {
            TutorPrompt.Exchange(student: "student \($0)", tutor: "tutor \($0)")
        }
        let prompt = TutorPrompt.build(topic: nil, history: history, studentSentence: "now")
        // Only the most recent turns survive; prefill time is the learner's wait.
        // Note "student 1" would match inside "student 10", so this checks an
        // unambiguous earlier turn instead.
        #expect(!prompt.contains("student 8"))
        #expect(prompt.contains("student 9"))
        #expect(prompt.contains("student 10"))
    }

    @Test("escapes quotes in replayed history")
    func escapesHistory() {
        let history = [TutorPrompt.Exchange(student: "x", tutor: "He said \"hi\"")]
        let prompt = TutorPrompt.build(topic: nil, history: history, studentSentence: "y")
        // An unescaped quote here would corrupt the replayed JSON and confuse the
        // model about the shape it is meant to produce.
        #expect(prompt.contains("\\\"hi\\\""))
    }

    @Test("grammar puts reply before corrections")
    func grammarFieldOrder() {
        let gbnf = TutorGrammar.gbnf
        guard let replyAt = gbnf.range(of: "reply"),
              let correctionsAt = gbnf.range(of: "corrections")
        else {
            Issue.record("grammar is missing its fields")
            return
        }
        // Load-bearing: reply must be generated first so speech can start while the
        // corrections are still being produced.
        #expect(replyAt.lowerBound < correctionsAt.lowerBound)
        #expect(gbnf.contains("root ::="))
        #expect(gbnf.contains("word_order"))
    }
}
