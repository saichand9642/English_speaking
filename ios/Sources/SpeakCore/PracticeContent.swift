import Foundation

/// A conversation topic and the question that opens it.
public struct Topic: Equatable, Sendable {
    public let title: String
    public let opener: String
}

/// A sentence to read aloud, chosen to exercise one specific sound.
public struct ReadAloudSentence: Equatable, Sendable {
    public let text: String
    /// What this sentence is testing, shown above it.
    public let focus: String
}

/// The built-in practice material.
///
/// Everything is bundled rather than fetched, because the app has to work in
/// aeroplane mode. Topics rotate by day so there is something new to talk about
/// without needing a network or a content feed.
public enum PracticeContent {

    public static let topics: [Topic] = [
        Topic(title: "Your morning", opener: "Tell me what you did this morning, from waking up until now."),
        Topic(title: "Work today", opener: "What are you working on at the moment? Explain it to me simply."),
        Topic(title: "Food", opener: "What did you eat yesterday, and who cooked it?"),
        Topic(title: "Your home town", opener: "Describe the place you grew up in. What does it look like?"),
        Topic(title: "Weekend plans", opener: "What are you planning to do this weekend?"),
        Topic(title: "A difficult day", opener: "Tell me about a day recently that did not go well."),
        Topic(title: "Travel", opener: "Where was the last place you travelled to? How did you get there?"),
        Topic(title: "Family", opener: "Tell me about someone in your family you are close to."),
        Topic(title: "Something you learned", opener: "What is something new you learned in the last month?"),
        Topic(title: "Your commute", opener: "How do you get to work, and how long does it take?"),
        Topic(title: "Films and shows", opener: "What was the last film or show you watched? What happened in it?"),
        Topic(title: "Money", opener: "Explain something you saved up for and finally bought."),
        Topic(title: "Health", opener: "How do you keep yourself healthy? Has that changed recently?"),
        Topic(title: "A person you admire", opener: "Who do you admire, and what did they do?"),
        Topic(title: "Technology", opener: "What app or device do you use most? Why that one?"),
        Topic(title: "Weather", opener: "What is the weather like where you are, and how does it affect your day?"),
        Topic(title: "Shopping", opener: "Describe the last thing you bought and why you chose it."),
        Topic(title: "A mistake", opener: "Tell me about a mistake you made and what you did afterwards."),
        Topic(title: "Your ambitions", opener: "What do you want to be doing in five years?"),
        Topic(title: "Friends", opener: "How did you meet your closest friend?"),
        Topic(title: "Festivals", opener: "Describe a festival you celebrate. What happens on the day?"),
        Topic(title: "Sport", opener: "Do you follow or play any sport? Explain the rules to me."),
        Topic(title: "Reading", opener: "What was the last thing you read? Would you recommend it?"),
        Topic(title: "Your city now", opener: "What has changed in your city in the last few years?"),
        Topic(title: "Cooking", opener: "Explain step by step how you make something you cook well."),
        Topic(title: "A childhood memory", opener: "What is your earliest clear memory?"),
        Topic(title: "Work problems", opener: "Describe a problem at work and how your team solved it."),
        Topic(title: "Music", opener: "What music do you listen to while working or travelling?"),
        Topic(title: "Making decisions", opener: "Tell me about a decision you had to think hard about."),
        Topic(title: "Tomorrow", opener: "What does tomorrow look like for you, hour by hour?")
    ]

    /// Read-aloud material, weighted towards the sounds that most affect how easily
    /// Indian-English speech is understood elsewhere.
    public static let readAloudSentences: [ReadAloudSentence] = [
        // ---- v and w ----
        ReadAloudSentence(text: "We have very heavy rain in the village every winter.", focus: "The v and w sounds"),
        ReadAloudSentence(text: "Vivek will visit the new website when he wants advice.", focus: "The v and w sounds"),
        ReadAloudSentence(text: "The van was waiting outside the wide white wall.", focus: "The v and w sounds"),
        ReadAloudSentence(text: "Every winter we invite twelve visitors over.", focus: "The v and w sounds"),

        // ---- th ----
        ReadAloudSentence(text: "I think these three things are worth the trouble.", focus: "The th sound"),
        ReadAloudSentence(text: "They thought the weather on Thursday was better than this.", focus: "The th sound"),
        ReadAloudSentence(text: "Both brothers thanked them for the birthday gift.", focus: "The th sound"),
        ReadAloudSentence(text: "There are thirty-three months in that other theory.", focus: "The th sound"),

        // ---- short and long vowels ----
        ReadAloudSentence(text: "The ship on the beach is cheap to fill.", focus: "Short and long vowels"),
        ReadAloudSentence(text: "He will feel the heel of the full boot.", focus: "Short and long vowels"),
        ReadAloudSentence(text: "Sit in this seat and eat a little bit.", focus: "Short and long vowels"),
        ReadAloudSentence(text: "The fool pulled a full cart up the hill.", focus: "Short and long vowels"),

        // ---- a vowels ----
        ReadAloudSentence(text: "The cat sat on a hot cot in the back yard.", focus: "The a and o vowels"),
        ReadAloudSentence(text: "My aunt cannot stand the sand at all.", focus: "The a and o vowels"),

        // ---- s and z ----
        ReadAloudSentence(text: "The zoo closes at six, so please choose quickly.", focus: "The s and z sounds"),
        ReadAloudSentence(text: "His eyes were busy as the buses passed.", focus: "The s and z sounds"),

        // ---- f and p ----
        ReadAloudSentence(text: "Please find four perfect photographs for the file.", focus: "The f and p sounds"),

        // ---- consonant clusters ----
        ReadAloudSentence(text: "She stopped the school bus at the station street.", focus: "Consonant clusters"),
        ReadAloudSentence(text: "The strong sports team scored twelve straight points.", focus: "Consonant clusters"),
        ReadAloudSentence(text: "He asked for the desks to be moved next Tuesday.", focus: "Consonant clusters"),

        // ---- word stress and rhythm ----
        ReadAloudSentence(text: "I would like to discuss the report before the meeting.", focus: "Rhythm and stress"),
        ReadAloudSentence(text: "Photography is a comfortable hobby for a vegetable farmer.", focus: "Rhythm and stress"),
        ReadAloudSentence(text: "The development of the department was necessary.", focus: "Rhythm and stress"),
        ReadAloudSentence(text: "Yesterday I finished the interesting chapter about temperature.", focus: "Rhythm and stress"),
        ReadAloudSentence(text: "She particularly enjoyed the comfortable temperature yesterday.", focus: "Rhythm and stress"),

        // ---- everyday fluency ----
        ReadAloudSentence(text: "Could you tell me where the nearest bank is, please?", focus: "Everyday speech"),
        ReadAloudSentence(text: "I have been working here for about three years now.", focus: "Everyday speech"),
        ReadAloudSentence(text: "If I had known earlier, I would have called you.", focus: "Everyday speech"),
        ReadAloudSentence(text: "She said she was going to finish it by Friday evening.", focus: "Everyday speech"),
        ReadAloudSentence(text: "There is not much difference between these two options.", focus: "Everyday speech")
    ]

    /// Rotates topics by day so each day opens with something different.
    public static func topic(forEpochDay day: Int64) -> Topic {
        let count = Int64(topics.count)
        let index = Int(((day % count) + count) % count)
        return topics[index]
    }

    public static func sentence(at index: Int) -> ReadAloudSentence {
        let count = readAloudSentences.count
        return readAloudSentences[((index % count) + count) % count]
    }

    /// Today, as an epoch day number, matching the Android build's storage format.
    public static func todayEpochDay(now: Date = Date(), calendar: Calendar = .current) -> Int64 {
        let start = calendar.startOfDay(for: now)
        return Int64(start.timeIntervalSince1970 / 86_400)
    }
}
