import Foundation

/// Deterministic checks for the handful of errors that Indian-English speakers make
/// most often and that a 1B model is least reliable about.
///
/// This exists because the two failure modes of a small model pull in opposite
/// directions: it invents mistakes that are not there, and it misses the common ones
/// that matter. `CorrectionVerifier` handles the first. This handles the second, with
/// no model involved at all — every rule below is a pattern with one unambiguous
/// fix, so it can never produce a false positive of the "you said something wrong
/// that you did not say" kind.
///
/// Rules are intentionally conservative. Anything context-dependent is left to the
/// model rather than guessed at here.
public enum RuleBasedChecker {

    /// Nouns that can only be owned, never "had" in the progressive sense. An
    /// optional quantifier is allowed in front so "two brothers" and "a car" match.
    ///
    /// This constraint is load-bearing: "I am having lunch" and "I'm having trouble
    /// with my laptop" are perfectly good English, so matching on "am having" alone
    /// produces exactly the invented mistake this app must never make.
    private static let possession =
        #"(?:(?:a|an|one|two|three|four|five|six|seven|eight|nine|ten|many|some|no|\d+)\s+)?"#
        + #"(?:brothers?|sisters?|cousins?|sons?|daughters?|cars?|bikes?|houses?|phones?|laptops?|doubts?|money)"#

    /// `@unchecked Sendable` is justified rather than convenient: Apple documents
    /// `NSRegularExpression` as immutable and thread-safe once constructed, the
    /// replacement closures are pure, and every stored property is a `let`. Nothing
    /// here has mutable shared state for the compiler to protect.
    private struct Rule: @unchecked Sendable {
        let regex: NSRegularExpression
        let category: MistakeCategory
        let explanation: String
        let replace: @Sendable ([String?]) -> String
    }

    private static func rule(
        _ pattern: String,
        _ category: MistakeCategory,
        _ explanation: String,
        _ replace: @escaping @Sendable ([String?]) -> String
    ) -> Rule? {
        guard let regex = try? NSRegularExpression(
            pattern: pattern,
            options: [.caseInsensitive]
        ) else { return nil }
        return Rule(regex: regex, category: category, explanation: explanation, replace: replace)
    }

    private static let plainForms: [String: String] = [
        "went": "go", "came": "come", "saw": "see", "ate": "eat",
        "took": "take", "gave": "give", "made": "make", "said": "say",
        "got": "get", "had": "have", "knew": "know", "told": "tell",
        "found": "find", "left": "leave", "felt": "feel",
        "brought": "bring", "bought": "buy", "thought": "think",
        "caught": "catch", "taught": "teach"
    ]

    private static let rules: [Rule] = [
        // ---- verbs that never take "about" ----
        rule(
            #"\b(discuss|discussed|discussing)\s+about\b"#, .preposition,
            "\"Discuss\" already means \"talk about\", so it does not need \"about\" after it."
        ) { g in g[1] ?? "" },

        // ---- redundant "back" ----
        rule(
            #"\b(return|returned|revert|reverted)\s+back\b"#, .wordChoice,
            "\"Return\" already means \"come back\", so \"back\" is repeated."
        ) { g in g[1] ?? "" },

        // ---- duration: since vs for ----
        rule(
            #"\bsince\s+(\d+|a|two|three|four|five|six|seven|eight|nine|ten)\s+"#
            + #"(year|years|month|months|week|weeks|day|days|hour|hours)\b"#,
            .preposition,
            "Use \"for\" with a length of time and \"since\" with a starting point: "
            + "\"for two years\", but \"since 2019\"."
        ) { g in "for \(g[1] ?? "") \(g[2] ?? "")" },

        // ---- one of my + singular ----
        rule(
            #"\bone of my (friend|brother|sister|cousin|colleague|teacher|student)\b"#,
            .plural,
            "\"One of\" picks one out of a group, so the noun after it is plural."
        ) { g in "one of my \(g[1] ?? "")s" },

        // ---- uncountable nouns ----
        rule(
            #"\b(informations|advices|equipments|furnitures|luggages|slangs|softwares|staffs)\b"#,
            .plural,
            "This word has no plural in English, so it never takes an \"s\"."
        ) { g in String((g[1] ?? "").dropLast()) },

        // ---- did/didn't + past tense ----
        rule(
            #"\b(did|didn't|did not)\s+"#
            + #"(went|came|saw|ate|took|gave|made|said|got|had|knew|told|found|left|felt|"#
            + #"brought|bought|thought|caught|taught)\b"#,
            .tense,
            "After \"did\" or \"didn't\", the verb goes back to its plain form."
        ) { g in
            let verb = (g[2] ?? "").lowercased()
            return "\(g[1] ?? "") \(plainForms[verb] ?? verb)"
        },

        // ---- double comparative ----
        rule(
            #"\bmore\s+(better|worse|easier|faster|slower|bigger|smaller|older|younger|cheaper|closer)\b"#,
            .wordChoice,
            "This word already compares two things, so \"more\" in front of it is one "
            + "comparison too many."
        ) { g in g[1] ?? "" },

        // ---- stative "have" in the continuous ----
        rule(
            #"\b(I|we|they|you)\s+(?:am|are)\s+having\s+("# + possession + #")\b"#,
            .tense,
            "When \"have\" means \"own\", English uses the plain form, not \"-ing\"."
        ) { g in "\(g[1] ?? "") have \(g[2] ?? "")" },

        rule(
            #"\b(he|she|it)\s+is\s+having\s+("# + possession + #")\b"#,
            .tense,
            "When \"have\" means \"own\", English uses the plain form, not \"-ing\"."
        ) { g in "\(g[1] ?? "") has \(g[2] ?? "")" },

        rule(
            #"\b(I|we|they|you)\s+(?:am|are)\s+"#
            + #"(knowing|understanding|wanting|needing|liking|believing)\b"#,
            .tense,
            "Verbs about thinking and feeling stay in their simple form, without \"-ing\"."
        ) { g in
            var plain = String((g[2] ?? "").lowercased().dropLast(3))
            if plain == "believ" || plain == "lik" { plain += "e" }
            return "\(g[1] ?? "") \(plain)"
        },

        // ---- questions formed as statements ----
        rule(
            #"\b(what|where|when|why|how)\s+(you|he|she|it|they|we)\s+(are|is|am|were|was)\b"#,
            .wordOrder,
            "In a question the helper verb comes before the person: \"where are you\", "
            + "not \"where you are\"."
        ) { g in "\(g[1] ?? "") \(g[3] ?? "") \(g[2] ?? "")" },

        // ---- Indian-English set phrases ----
        rule(
            #"\bcousin\s+(brother|sister)\b"#, .wordChoice,
            "In English \"cousin\" covers both, so nothing is added after it."
        ) { _ in "cousin" },

        rule(
            #"\bmy\s+good\s+name\b"#, .wordChoice,
            "\"Good name\" is not used in English. Just say \"my name\"."
        ) { _ in "my name" },

        rule(
            #"\byour\s+good\s+name\b"#, .wordChoice,
            "\"Good name\" is not used in English. Just say \"your name\"."
        ) { _ in "your name" },

        rule(
            #"\bout\s+of\s+station\b"#, .wordChoice,
            "English speakers say \"out of town\" when you are away."
        ) { _ in "out of town" },

        rule(
            #"\bdo\s+the\s+needful\b"#, .wordChoice,
            "This phrase is not used in English. Say what you actually want done."
        ) { _ in "do what is needed" },

        rule(
            #"\bprepone\b"#, .wordChoice,
            "\"Prepone\" is not an English word. Say the meeting was moved earlier."
        ) { _ in "bring forward" },

        rule(
            #"\b(morning|evening|afternoon|night)\s+time\b"#, .wordChoice,
            "\"Time\" is not needed here: \"in the morning\" is enough."
        ) { g in g[1] ?? "" },

        rule(
            #"\bmyself\s+([A-Z][a-z]+)\b"#, .wordOrder,
            "To introduce yourself say \"I am\" and then your name."
        ) { g in "I am \(g[1] ?? "")" }
    ].compactMap { $0 }

    public static func check(_ transcript: String) -> [Correction] {
        guard !transcript.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty else {
            return []
        }
        let full = NSRange(transcript.startIndex..., in: transcript)
        var found: [Correction] = []
        var claimed: [NSRange] = []

        for rule in rules {
            for match in rule.regex.matches(in: transcript, options: [], range: full) {
                // Do not report two rules over the same words.
                if claimed.contains(where: { NSIntersectionRange($0, match.range).length > 0 }) {
                    continue
                }
                guard let matchRange = Range(match.range, in: transcript) else { continue }
                let wrong = String(transcript[matchRange])
                    .trimmingCharacters(in: .whitespacesAndNewlines)
                let right = rule.replace(groups(match, in: transcript))
                    .trimmingCharacters(in: .whitespacesAndNewlines)
                if wrong.lowercased() == right.lowercased() { continue }

                claimed.append(match.range)
                found.append(
                    Correction(
                        wrong: wrong,
                        right: right,
                        explanation: rule.explanation,
                        category: rule.category
                    )
                )
            }
        }
        return found
    }

    /// Applies every rule to produce a fully corrected sentence. Used as the
    /// corrected form when the model produced nothing usable.
    public static func applyAll(_ transcript: String) -> String {
        var result = transcript
        for correction in check(transcript) {
            guard let range = result.range(of: correction.wrong, options: [.caseInsensitive])
            else { continue }
            result.replaceSubrange(range, with: correction.right)
        }
        return result
    }

    private static func groups(
        _ match: NSTextCheckingResult,
        in text: String
    ) -> [String?] {
        (0..<match.numberOfRanges).map { index in
            let range = match.range(at: index)
            guard range.location != NSNotFound, let swiftRange = Range(range, in: text) else {
                return nil
            }
            return String(text[swiftRange])
        }
    }
}
