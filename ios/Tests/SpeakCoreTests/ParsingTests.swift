import Testing
@testable import SpeakCore

@Suite("Tutor response parser")
struct TutorResponseParserTests {

    @Test("parses a well formed response")
    func wellFormed() {
        let raw = """
        {"reply": "Nice. What did you buy?",
         "corrected": "Yesterday I went to the market.",
         "corrections": [
           {"wrong": "I go", "right": "I went", "why": "It happened yesterday.", "type": "tense"}
         ]}
        """
        let feedback = TutorResponseParser.parse(
            raw: raw, transcript: "Yesterday I go to the market"
        )
        #expect(feedback.spokenReply == "Nice. What did you buy?")
        #expect(feedback.correctedSentence == "Yesterday I went to the market.")
        #expect(feedback.corrections.count == 1)
        #expect(feedback.corrections.first?.category == .tense)
    }

    @Test("strips markdown fences and surrounding prose")
    func stripsFences() {
        let raw = """
        Sure! Here is the feedback:
        ```json
        {"reply": "Good.", "corrected": "I am fine.", "corrections": []}
        ```
        """
        let feedback = TutorResponseParser.parse(raw: raw, transcript: "I am fine")
        #expect(feedback.spokenReply == "Good.")
        #expect(feedback.corrections.isEmpty)
    }

    @Test("braces inside a string do not end the object early")
    func bracesInString() {
        // A naive first-brace/last-brace pair breaks on this input.
        let raw = #"{"reply": "Use {this} form.", "corrected": "", "corrections": []}"#
        let feedback = TutorResponseParser.parse(raw: raw, transcript: "something")
        #expect(feedback.spokenReply == "Use {this} form.")
    }

    @Test("escaped quotes inside a string are handled")
    func escapedQuotes() {
        let raw = #"{"reply": "Say \"hello\" first.", "corrected": "", "corrections": []}"#
        let feedback = TutorResponseParser.parse(raw: raw, transcript: "something")
        #expect(feedback.spokenReply == "Say \"hello\" first.")
    }

    @Test("unparseable output still yields a usable reply")
    func unparseable() {
        // Failing safe means the conversation continues and no mistakes are claimed.
        let feedback = TutorResponseParser.parse(
            raw: "I think that was good!", transcript: "I am fine"
        )
        #expect(feedback.spokenReply == "I think that was good!")
        #expect(feedback.corrections.isEmpty)
        #expect(feedback.correctedSentence == nil)
    }

    @Test("hallucinated corrections are discarded during parsing")
    func discardsHallucinations() {
        let raw = """
        {"reply": "Ok.", "corrected": "I have two brothers.",
         "corrections": [{"wrong": "I has", "right": "I have", "why": "x", "type": "tense"}]}
        """
        // The learner never said "I has", so nothing survives and the corrected
        // sentence is dropped along with it.
        let feedback = TutorResponseParser.parse(raw: raw, transcript: "I have two brothers")
        #expect(feedback.corrections.isEmpty)
        #expect(feedback.correctedSentence == nil)
        #expect(feedback.wasCorrect)
    }

    @Test("accepts alternative field names")
    func alternativeFieldNames() {
        let raw = """
        {"say": "Fine.", "corrected": "",
         "corrections": [{"was": "I go", "fix": "I went", "explanation": "Past.",
                          "category": "verb tense"}]}
        """
        let feedback = TutorResponseParser.parse(raw: raw, transcript: "Yesterday I go there")
        #expect(feedback.spokenReply == "Fine.")
        #expect(feedback.corrections.count == 1)
        #expect(feedback.corrections.first?.category == .tense)
    }

    @Test("extracts the first balanced object")
    func extractsBalanced() {
        let found = TutorResponseParser.extractJSONObject(#"noise {"a": {"b": 1}} trailing"#)
        #expect(found == #"{"a": {"b": 1}}"#)
    }
}

@Suite("Rule based checker")
struct RuleBasedCheckerTests {

    private func firstFix(_ sentence: String) -> String? {
        RuleBasedChecker.check(sentence).first?.right
    }

    @Test("discuss about")
    func discussAbout() {
        let found = RuleBasedChecker.check("We discussed about the plan")
        #expect(found.count == 1)
        #expect(found.first?.right == "discussed")
        #expect(found.first?.category == .preposition)
    }

    @Test("return back")
    func returnBack() {
        #expect(firstFix("Please return back the book") == "return")
    }

    @Test("since with a duration")
    func sinceDuration() {
        let found = RuleBasedChecker.check("I am working here since 3 years")
        #expect(found.contains { $0.right.lowercased() == "for 3 years" })
    }

    @Test("one of my friend")
    func oneOfMyFriend() {
        #expect(firstFix("one of my friend came") == "one of my friends")
    }

    @Test("uncountable plural")
    func uncountablePlural() {
        #expect(firstFix("He gave me many informations") == "information")
    }

    @Test("did plus past tense")
    func didPlusPast() {
        let found = RuleBasedChecker.check("I didn't went there")
        #expect(found.count == 1)
        #expect(found.first?.right == "didn't go")
        #expect(found.first?.category == .tense)
    }

    @Test("double comparative")
    func doubleComparative() {
        #expect(firstFix("This one is more better") == "better")
    }

    @Test("stative having with a possession")
    func stativeHaving() {
        #expect(firstFix("He is having two brothers") == "He has two brothers")
        #expect(firstFix("I am having a car") == "I have a car")
    }

    @Test("progressive having with a meal is left alone")
    func progressiveHavingAllowed() {
        // "I am having lunch" is correct English. Flagging it would be exactly the
        // invented mistake this app promises never to make.
        #expect(RuleBasedChecker.check("I am having lunch right now").isEmpty)
        #expect(RuleBasedChecker.check("We are having a party tomorrow").isEmpty)
        #expect(RuleBasedChecker.check("I am having trouble with my laptop").isEmpty)
    }

    @Test("stative knowing")
    func stativeKnowing() {
        #expect(firstFix("I am knowing the answer") == "I know")
    }

    @Test("question word order")
    func questionWordOrder() {
        let found = RuleBasedChecker.check("Where you are going?")
        #expect(found.count == 1)
        #expect(found.first?.right == "Where are you")
        #expect(found.first?.category == .wordOrder)
    }

    @Test("Indian-English set phrases")
    func setPhrases() {
        #expect(firstFix("My cousin brother is here") == "cousin")
        #expect(firstFix("What is your good name?") == "your name")
        #expect(firstFix("I am out of station this week") == "out of town")
        #expect(firstFix("I go in the morning time") == "morning")
        #expect(!RuleBasedChecker.check("Can we prepone the meeting?").isEmpty)
    }

    @Test("correct sentences are left alone")
    func noFalsePositives() {
        let clean = [
            "Yesterday I went to the market and bought some rice.",
            "I have been working here for three years.",
            "He has two brothers and one sister.",
            "Where are you going tomorrow?",
            "I did not go to the office yesterday.",
            "This option is better than the other one.",
            "We discussed the plan this morning.",
            "One of my friends lives in Delhi.",
            "She gave me some useful information.",
            "I am having lunch right now."
        ]
        for sentence in clean {
            #expect(
                RuleBasedChecker.check(sentence).isEmpty,
                "false positive on: \(sentence)"
            )
        }
    }

    @Test("empty input yields nothing")
    func emptyInput() {
        #expect(RuleBasedChecker.check("").isEmpty)
        #expect(RuleBasedChecker.check("   ").isEmpty)
    }

    @Test("two rules never claim the same words")
    func noOverlap() {
        let found = RuleBasedChecker.check("I didn't went and we discussed about it")
        #expect(found.count == 2)
    }

    @Test("applyAll rewrites the whole sentence")
    func applyAll() {
        let fixed = RuleBasedChecker.applyAll("We discussed about one of my friend")
        #expect(fixed.contains("discussed one of my friends"))
    }

    @Test("applyAll leaves a correct sentence untouched")
    func applyAllNoOp() {
        let sentence = "I went to the market yesterday."
        #expect(RuleBasedChecker.applyAll(sentence) == sentence)
    }

    @Test("every rule supplies an explanation and a real change")
    func everyRuleIsWellFormed() {
        let samples = [
            "We discussed about it", "Please return back", "since 5 years",
            "one of my friend", "many informations", "I didn't went",
            "more better", "He is having two cars", "I am knowing it",
            "Where you are going", "my cousin brother", "your good name",
            "out of station", "prepone it", "in the evening time"
        ]
        for sentence in samples {
            for correction in RuleBasedChecker.check(sentence) {
                #expect(!correction.explanation.isEmpty, "blank why on: \(sentence)")
                #expect(
                    correction.wrong.lowercased() != correction.right.lowercased(),
                    "no-op fix on: \(sentence)"
                )
            }
        }
    }
}
