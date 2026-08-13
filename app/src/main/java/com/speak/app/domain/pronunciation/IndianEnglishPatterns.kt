package com.speak.app.domain.pronunciation

/**
 * The substitutions that most often make Indian-English speech hard for other
 * English speakers to follow, each with a physical instruction rather than a
 * phonetic symbol.
 *
 * These are used only where they can be checked: in Read-aloud, where the target
 * sentence is known, a mismatch between the target word and the recognised word
 * can be tested against these patterns directly. That is a real observation about
 * the audio, not a guess from spelling.
 */
object IndianEnglishPatterns {

    /**
     * @param expectedFragment the letters in the intended word
     * @param heardFragment what appears instead when the substitution happens
     * @param tip a physical instruction: tongue, lips, or stress
     */
    data class Substitution(
        val expectedFragment: String,
        val heardFragment: String,
        val name: String,
        val tip: String
    )

    val substitutions: List<Substitution> = listOf(
        // ---- v / w ----
        Substitution("v", "w", "v sounding like w",
            "For \"v\", press your top teeth lightly onto your bottom lip and hum. For \"w\", the lips round and the teeth stay clear."),
        Substitution("w", "v", "w sounding like v",
            "For \"w\", round your lips like a small kiss and keep your teeth away from your lip."),

        // ---- th ----
        Substitution("th", "t", "th sounding like t",
            "Let your tongue tip touch the back of your top teeth and push air through. Don't tap it like a hard \"t\"."),
        Substitution("th", "d", "th sounding like d",
            "Put your tongue tip gently between your teeth and let it buzz, instead of tapping behind the teeth."),
        Substitution("th", "s", "th sounding like s",
            "The tongue must touch the teeth for \"th\". If air hisses past a tongue that stays back, it becomes \"s\"."),

        // ---- short and long vowels ----
        Substitution("ee", "i", "long ee cut short",
            "Hold the vowel longer and spread your lips into a slight smile: \"sheep\" is much longer than \"ship\"."),
        Substitution("i", "ee", "short i stretched",
            "Keep this vowel short and relax your lips. \"Ship\" is a quick sound, not a long one."),
        Substitution("oo", "u", "long oo cut short",
            "Round your lips firmly and hold it: \"fool\" is longer than \"full\"."),
        Substitution("a", "o", "a pulled towards o",
            "Drop your jaw and spread your lips a little for this \"a\", so \"cat\" does not drift towards \"cot\"."),

        // ---- s / z ----
        Substitution("z", "j", "z sounding like j",
            "Let the air flow continuously for \"z\" instead of starting it with a tongue tap."),
        Substitution("z", "s", "z losing its buzz",
            "Add voice to it: put a hand on your throat and you should feel a buzz for \"z\", none for \"s\"."),

        // ---- p / f ----
        Substitution("f", "p", "f sounding like p",
            "For \"f\", rest your top teeth on your bottom lip and let air stream out. Don't close your lips fully."),

        // ---- consonant clusters ----
        Substitution("sk", "isk", "extra vowel before a cluster",
            "Start the two consonants together, with no vowel in front of them."),
        Substitution("st", "ist", "extra vowel before a cluster",
            "Begin directly on the \"s\" and slide into the \"t\", with no vowel before it.")
    )

    /**
     * Finds the substitution that best explains hearing [heard] when [expected]
     * was intended, or null when nothing in the table applies.
     */
    fun explain(expected: String, heard: String): Substitution? {
        val target = expected.lowercase().trim { !it.isLetter() }
        val actual = heard.lowercase().trim { !it.isLetter() }
        if (target.isEmpty() || actual.isEmpty() || target == actual) return null

        return substitutions.firstOrNull { substitution ->
            target.contains(substitution.expectedFragment) &&
                actual.contains(substitution.heardFragment) &&
                // Guard against a coincidental match: the words must actually be
                // close enough that a single substitution could explain the gap.
                editDistance(target, actual) <= maxOf(2, target.length / 3)
        }
    }

    /** Generic advice when the audio was unclear but no known pattern fits. */
    fun genericTip(word: String): String = when {
        word.length >= 8 ->
            "Say it slowly in parts and put the stress on one syllable only. Long words lose their shape when every syllable gets equal weight."
        word.count { it in "aeiou" } <= 1 && word.length >= 4 ->
            "Open your mouth a little wider on the vowel and finish the last consonant clearly."
        else ->
            "Slow down on this word and finish the final sound before moving on."
    }

    internal fun editDistance(a: String, b: String): Int {
        if (a == b) return 0
        if (a.isEmpty()) return b.length
        if (b.isEmpty()) return a.length
        var previous = IntArray(b.length + 1) { it }
        var current = IntArray(b.length + 1)
        for (i in 1..a.length) {
            current[0] = i
            for (j in 1..b.length) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                current[j] = minOf(current[j - 1] + 1, previous[j] + 1, previous[j - 1] + cost)
            }
            val swap = previous
            previous = current
            current = swap
        }
        return previous[b.length]
    }
}
