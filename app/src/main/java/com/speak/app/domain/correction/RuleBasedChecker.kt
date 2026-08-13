package com.speak.app.domain.correction

import com.speak.app.domain.model.Correction
import com.speak.app.domain.model.MistakeCategory

/**
 * Deterministic checks for the handful of errors that Indian-English speakers
 * make most often and that a 1B model is least reliable about.
 *
 * This exists because the two failure modes of a small model pull in opposite
 * directions: it invents mistakes that are not there, and it misses the common
 * ones that matter. [CorrectionVerifier] handles the first. This handles the
 * second, with no model involved at all -- every rule below is a pattern with
 * one unambiguous fix, so it can never produce a false positive of the "you said
 * something wrong that you did not say" kind.
 *
 * Rules are intentionally conservative. Anything context-dependent is left to
 * the model rather than guessed at here.
 */
object RuleBasedChecker {

    /**
     * Nouns that can only be owned, never "had" in the progressive sense. An
     * optional quantifier is allowed in front so "two brothers" and "a car" match.
     */
    private const val POSSESSION =
        """(?:(?:a|an|one|two|three|four|five|six|seven|eight|nine|ten|many|some|no|\d+)\s+)?""" +
            """(?:brothers?|sisters?|cousins?|sons?|daughters?|cars?|bikes?|houses?|phones?|laptops?|doubts?|money)"""

    private data class Rule(
        val pattern: Regex,
        val category: MistakeCategory,
        val explanation: String,
        val replace: (MatchResult) -> String
    )

    private fun rule(
        regex: String,
        category: MistakeCategory,
        explanation: String,
        replace: (MatchResult) -> String
    ) = Rule(Regex(regex, RegexOption.IGNORE_CASE), category, explanation, replace)

    private val rules: List<Rule> = listOf(
        // ---- verbs that never take "about" ----
        rule("""\b(discuss|discussed|discussing)\s+about\b""", MistakeCategory.PREPOSITION,
            "\"Discuss\" already means \"talk about\", so it does not need \"about\" after it.") { m ->
            m.groupValues[1]
        },

        // ---- redundant "back" ----
        rule("""\b(return|returned|revert|reverted)\s+back\b""", MistakeCategory.WORD_CHOICE,
            "\"Return\" already means \"come back\", so \"back\" is repeated.") { m ->
            m.groupValues[1]
        },

        // ---- duration: since vs for ----
        rule("""\bsince\s+(\d+|a|two|three|four|five|six|seven|eight|nine|ten)\s+(year|years|month|months|week|weeks|day|days|hour|hours)\b""",
            MistakeCategory.PREPOSITION,
            "Use \"for\" with a length of time and \"since\" with a starting point: \"for two years\", but \"since 2019\".") { m ->
            "for ${m.groupValues[1]} ${m.groupValues[2]}"
        },

        // ---- one of my + singular ----
        rule("""\bone of my (friend|brother|sister|cousin|colleague|teacher|student)\b""",
            MistakeCategory.PLURAL,
            "\"One of\" picks one out of a group, so the noun after it is plural.") { m ->
            "one of my ${m.groupValues[1]}s"
        },

        // ---- uncountable nouns ----
        rule("""\b(informations|advices|equipments|furnitures|luggages|slangs|softwares|staffs)\b""",
            MistakeCategory.PLURAL,
            "This word has no plural in English, so it never takes an \"s\".") { m ->
            m.groupValues[1].dropLast(1)
        },

        // ---- did/didn't + past tense ----
        rule("""\b(did|didn't|did not)\s+(went|came|saw|ate|took|gave|made|said|got|had|knew|told|found|left|felt|brought|bought|thought|caught|taught)\b""",
            MistakeCategory.TENSE,
            "After \"did\" or \"didn't\", the verb goes back to its plain form.") { m ->
            val plain = mapOf(
                "went" to "go", "came" to "come", "saw" to "see", "ate" to "eat",
                "took" to "take", "gave" to "give", "made" to "make", "said" to "say",
                "got" to "get", "had" to "have", "knew" to "know", "told" to "tell",
                "found" to "find", "left" to "leave", "felt" to "feel",
                "brought" to "bring", "bought" to "buy", "thought" to "think",
                "caught" to "catch", "taught" to "teach"
            )
            val verb = m.groupValues[2].lowercase()
            "${m.groupValues[1]} ${plain[verb] ?: verb}"
        },

        // ---- double comparative ----
        rule("""\bmore\s+(better|worse|easier|faster|slower|bigger|smaller|older|younger|cheaper|closer)\b""",
            MistakeCategory.WORD_CHOICE,
            "This word already compares two things, so \"more\" in front of it is one comparison too many.") { m ->
            m.groupValues[1]
        },

        // ---- stative "have" in the continuous ----
        // Only fires when the object is unmistakably a possession. "I am having
        // lunch" and "I'm having trouble with my laptop" are perfectly good
        // English, so matching on "am having" alone produces false positives --
        // which is exactly the kind of invented mistake this app must not make.
        rule(
            """\b(I|we|they|you)\s+(?:am|are)\s+having\s+($POSSESSION)\b""",
            MistakeCategory.TENSE,
            "When \"have\" means \"own\", English uses the plain form, not \"-ing\"."
        ) { m -> "${m.groupValues[1]} have ${m.groupValues[2]}" },

        rule(
            """\b(he|she|it)\s+is\s+having\s+($POSSESSION)\b""",
            MistakeCategory.TENSE,
            "When \"have\" means \"own\", English uses the plain form, not \"-ing\"."
        ) { m -> "${m.groupValues[1]} has ${m.groupValues[2]}" },
        rule("""\b(I|we|they|you)\s+(?:am|are)\s+(knowing|understanding|wanting|needing|liking|believing)\b""",
            MistakeCategory.TENSE,
            "Verbs about thinking and feeling stay in their simple form, without \"-ing\".") { m ->
            val plain = m.groupValues[2].lowercase().removeSuffix("ing")
                .let { if (it == "believ" || it == "lik") it + "e" else it }
            "${m.groupValues[1]} $plain"
        },

        // ---- questions formed as statements ----
        rule("""\b(what|where|when|why|how)\s+(you|he|she|it|they|we)\s+(are|is|am|were|was)\b""",
            MistakeCategory.WORD_ORDER,
            "In a question the helper verb comes before the person: \"where are you\", not \"where you are\".") { m ->
            "${m.groupValues[1]} ${m.groupValues[3]} ${m.groupValues[2]}"
        },

        // ---- Indian-English set phrases ----
        rule("""\bcousin\s+(brother|sister)\b""", MistakeCategory.WORD_CHOICE,
            "In English \"cousin\" covers both, so nothing is added after it.") { _ -> "cousin" },
        rule("""\bmy\s+good\s+name\b""", MistakeCategory.WORD_CHOICE,
            "\"Good name\" is not used in English. Just say \"my name\".") { _ -> "my name" },
        rule("""\byour\s+good\s+name\b""", MistakeCategory.WORD_CHOICE,
            "\"Good name\" is not used in English. Just say \"your name\".") { _ -> "your name" },
        rule("""\bout\s+of\s+station\b""", MistakeCategory.WORD_CHOICE,
            "English speakers say \"out of town\" when you are away.") { _ -> "out of town" },
        rule("""\bdo\s+the\s+needful\b""", MistakeCategory.WORD_CHOICE,
            "This phrase is not used in English. Say what you actually want done.") { _ ->
            "do what is needed"
        },
        rule("""\bprepone\b""", MistakeCategory.WORD_CHOICE,
            "\"Prepone\" is not an English word. Say the meeting was moved earlier.") { _ ->
            "bring forward"
        },
        rule("""\b(morning|evening|afternoon|night)\s+time\b""", MistakeCategory.WORD_CHOICE,
            "\"Time\" is not needed here: \"in the morning\" is enough.") { m ->
            m.groupValues[1]
        },
        rule("""\bmyself\s+([A-Z][a-z]+)\b""", MistakeCategory.WORD_ORDER,
            "To introduce yourself say \"I am\" and then your name.") { m ->
            "I am ${m.groupValues[1]}"
        }
    )

    fun check(transcript: String): List<Correction> {
        if (transcript.isBlank()) return emptyList()
        val found = mutableListOf<Correction>()
        val claimed = mutableListOf<IntRange>()

        for (rule in rules) {
            for (match in rule.pattern.findAll(transcript)) {
                // Do not report two rules over the same words.
                if (claimed.any { it.overlaps(match.range) }) continue
                val wrong = match.value.trim()
                val right = rule.replace(match).trim()
                if (wrong.equals(right, ignoreCase = true)) continue
                claimed += match.range
                found += Correction(
                    wrong = wrong,
                    right = right,
                    explanation = rule.explanation,
                    category = rule.category
                )
            }
        }
        return found
    }

    /**
     * Applies every rule to produce a fully corrected sentence. Used as the
     * corrected form when the model produced nothing usable.
     */
    fun applyAll(transcript: String): String {
        var result = transcript
        for (correction in check(transcript)) {
            result = result.replaceFirst(correction.wrong, correction.right, ignoreCase = true)
        }
        return result
    }

    private fun IntRange.overlaps(other: IntRange): Boolean =
        first <= other.last && other.first <= last

    private fun String.replaceFirst(old: String, new: String, ignoreCase: Boolean): String {
        val index = indexOf(old, ignoreCase = ignoreCase)
        return if (index < 0) this else substring(0, index) + new + substring(index + old.length)
    }
}
