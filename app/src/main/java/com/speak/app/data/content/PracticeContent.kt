package com.speak.app.data.content

/** A conversation topic and the question that opens it. */
data class Topic(val title: String, val opener: String)

/** A sentence to read aloud, chosen to exercise one specific sound. */
data class ReadAloudSentence(
    val text: String,
    /** What this sentence is testing, shown above it. */
    val focus: String
)

/**
 * The built-in practice material.
 *
 * Everything is bundled rather than fetched, because the app has to work in
 * aeroplane mode. Topics rotate by day so there is something new to talk about
 * without needing a network or a content feed.
 */
object PracticeContent {

    val topics: List<Topic> = listOf(
        Topic("Your morning", "Tell me what you did this morning, from waking up until now."),
        Topic("Work today", "What are you working on at the moment? Explain it to me simply."),
        Topic("Food", "What did you eat yesterday, and who cooked it?"),
        Topic("Your home town", "Describe the place you grew up in. What does it look like?"),
        Topic("Weekend plans", "What are you planning to do this weekend?"),
        Topic("A difficult day", "Tell me about a day recently that did not go well."),
        Topic("Travel", "Where was the last place you travelled to? How did you get there?"),
        Topic("Family", "Tell me about someone in your family you are close to."),
        Topic("Something you learned", "What is something new you learned in the last month?"),
        Topic("Your commute", "How do you get to work, and how long does it take?"),
        Topic("Films and shows", "What was the last film or show you watched? What happened in it?"),
        Topic("Money", "Explain something you saved up for and finally bought."),
        Topic("Health", "How do you keep yourself healthy? Has that changed recently?"),
        Topic("A person you admire", "Who do you admire, and what did they do?"),
        Topic("Technology", "What app or device do you use most? Why that one?"),
        Topic("Weather", "What is the weather like where you are, and how does it affect your day?"),
        Topic("Shopping", "Describe the last thing you bought and why you chose it."),
        Topic("A mistake", "Tell me about a mistake you made and what you did afterwards."),
        Topic("Your ambitions", "What do you want to be doing in five years?"),
        Topic("Friends", "How did you meet your closest friend?"),
        Topic("Festivals", "Describe a festival you celebrate. What happens on the day?"),
        Topic("Sport", "Do you follow or play any sport? Explain the rules to me."),
        Topic("Reading", "What was the last thing you read? Would you recommend it?"),
        Topic("Your city now", "What has changed in your city in the last few years?"),
        Topic("Cooking", "Explain step by step how you make something you cook well."),
        Topic("A childhood memory", "What is your earliest clear memory?"),
        Topic("Work problems", "Describe a problem at work and how your team solved it."),
        Topic("Music", "What music do you listen to while working or travelling?"),
        Topic("Making decisions", "Tell me about a decision you had to think hard about."),
        Topic("Tomorrow", "What does tomorrow look like for you, hour by hour?")
    )

    /**
     * Read-aloud material, weighted towards the sounds that most affect how easily
     * Indian-English speech is understood elsewhere.
     */
    val readAloudSentences: List<ReadAloudSentence> = listOf(
        // ---- v and w ----
        ReadAloudSentence("We have very heavy rain in the village every winter.", "The v and w sounds"),
        ReadAloudSentence("Vivek will visit the new website when he wants advice.", "The v and w sounds"),
        ReadAloudSentence("The van was waiting outside the wide white wall.", "The v and w sounds"),
        ReadAloudSentence("Every winter we invite twelve visitors over.", "The v and w sounds"),

        // ---- th ----
        ReadAloudSentence("I think these three things are worth the trouble.", "The th sound"),
        ReadAloudSentence("They thought the weather on Thursday was better than this.", "The th sound"),
        ReadAloudSentence("Both brothers thanked them for the birthday gift.", "The th sound"),
        ReadAloudSentence("There are thirty-three months in that other theory.", "The th sound"),

        // ---- short and long vowels ----
        ReadAloudSentence("The ship on the beach is cheap to fill.", "Short and long vowels"),
        ReadAloudSentence("He will feel the heel of the full boot.", "Short and long vowels"),
        ReadAloudSentence("Sit in this seat and eat a little bit.", "Short and long vowels"),
        ReadAloudSentence("The fool pulled a full cart up the hill.", "Short and long vowels"),

        // ---- a vowels ----
        ReadAloudSentence("The cat sat on a hot cot in the back yard.", "The a and o vowels"),
        ReadAloudSentence("My aunt cannot stand the sand at all.", "The a and o vowels"),

        // ---- s and z ----
        ReadAloudSentence("The zoo closes at six, so please choose quickly.", "The s and z sounds"),
        ReadAloudSentence("His eyes were busy as the buses passed.", "The s and z sounds"),

        // ---- f and p ----
        ReadAloudSentence("Please find four perfect photographs for the file.", "The f and p sounds"),

        // ---- consonant clusters ----
        ReadAloudSentence("She stopped the school bus at the station street.", "Consonant clusters"),
        ReadAloudSentence("The strong sports team scored twelve straight points.", "Consonant clusters"),
        ReadAloudSentence("He asked for the desks to be moved next Tuesday.", "Consonant clusters"),

        // ---- word stress and rhythm ----
        ReadAloudSentence("I would like to discuss the report before the meeting.", "Rhythm and stress"),
        ReadAloudSentence("Photography is a comfortable hobby for a vegetable farmer.", "Rhythm and stress"),
        ReadAloudSentence("The development of the department was necessary.", "Rhythm and stress"),
        ReadAloudSentence("Yesterday I finished the interesting chapter about temperature.", "Rhythm and stress"),
        ReadAloudSentence("She particularly enjoyed the comfortable temperature yesterday.", "Rhythm and stress"),

        // ---- everyday fluency ----
        ReadAloudSentence("Could you tell me where the nearest bank is, please?", "Everyday speech"),
        ReadAloudSentence("I have been working here for about three years now.", "Everyday speech"),
        ReadAloudSentence("If I had known earlier, I would have called you.", "Everyday speech"),
        ReadAloudSentence("She said she was going to finish it by Friday evening.", "Everyday speech"),
        ReadAloudSentence("There is not much difference between these two options.", "Everyday speech")
    )

    /** Rotates topics by day so each day opens with something different. */
    fun topicForDay(epochDay: Long): Topic = topics[(epochDay.mod(topics.size.toLong())).toInt()]

    fun sentenceAt(index: Int): ReadAloudSentence =
        readAloudSentences[index.mod(readAloudSentences.size)]
}
