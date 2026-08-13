import Foundation

/// How well the learner handled a drill item. Maps onto SM-2's 0...5 grades.
public enum DrillGrade: Sendable {
    /// Got it wrong, or could not produce it.
    case missed
    /// Got there, but hesitated or needed a second attempt.
    case hesitant
    /// Correct, at normal speed.
    case good
    /// Immediate and effortless.
    case easy

    public var quality: Int {
        switch self {
        case .missed: return 1
        case .hesitant: return 3
        case .good: return 4
        case .easy: return 5
        }
    }

    public var isPass: Bool { quality >= 3 }
}

/// Scheduling state for one drill item, in SM-2 terms.
public struct ReviewState: Equatable, Sendable {
    public static let initialEasiness = 2.5

    /// Consecutive successful reviews; reset to 0 on a lapse.
    public let repetitions: Int
    /// Days until the next review.
    public let intervalDays: Int
    /// SM-2's easiness factor, never below 1.3.
    public let easiness: Double
    /// The day this item next becomes due.
    public let dueEpochDay: Int64

    public init(
        repetitions: Int = 0,
        intervalDays: Int = 0,
        easiness: Double = ReviewState.initialEasiness,
        dueEpochDay: Int64 = 0
    ) {
        self.repetitions = repetitions
        self.intervalDays = intervalDays
        self.easiness = easiness
        self.dueEpochDay = dueEpochDay
    }
}

/// The SM-2 spaced-repetition algorithm.
///
/// Drills re-test the mistakes this particular speaker actually made, on the
/// schedule SM-2 dictates, rather than picking at random. That is the whole point:
/// the errors a learner repeats are the ones worth spending time on, and they need
/// to come back just as they are about to be forgotten.
public enum Sm2Scheduler {

    static let minEasiness = 1.3

    /// Hard ceiling so an item never disappears for years on end.
    static let maxIntervalDays = 365

    public static func schedule(
        state: ReviewState,
        grade: DrillGrade,
        todayEpochDay: Int64
    ) -> ReviewState {
        let quality = grade.quality

        // Easiness moves on every review, pass or fail. This is the standard SM-2
        // response-quality adjustment.
        let delta = 0.1 - Double(5 - quality) * (0.08 + Double(5 - quality) * 0.02)
        let easiness = max(state.easiness + delta, minEasiness)

        guard grade.isPass else {
            // A lapse sends the item back to the start of the ladder, but the
            // easiness it has earned is kept, so a hard item stays hard.
            return ReviewState(
                repetitions: 0,
                intervalDays: 1,
                easiness: easiness,
                dueEpochDay: todayEpochDay + 1
            )
        }

        let repetitions = state.repetitions + 1
        let rawInterval: Int
        switch repetitions {
        case 1: rawInterval = 1
        case 2: rawInterval = 6
        default:
            // Always move forward: with easiness pinned at the 1.3 floor, rounding
            // could otherwise leave the interval unchanged and the item would be
            // reviewed at the same spacing forever.
            rawInterval = max(
                Int((Double(state.intervalDays) * easiness).rounded()),
                state.intervalDays + 1
            )
        }
        let interval = min(rawInterval, maxIntervalDays)

        return ReviewState(
            repetitions: repetitions,
            intervalDays: interval,
            easiness: easiness,
            dueEpochDay: todayEpochDay + Int64(interval)
        )
    }

    /// A brand-new item is due immediately.
    public static func initial(todayEpochDay: Int64) -> ReviewState {
        ReviewState(dueEpochDay: todayEpochDay)
    }

    public static func isDue(_ state: ReviewState, todayEpochDay: Int64) -> Bool {
        state.dueEpochDay <= todayEpochDay
    }
}

/// Grades a spoken drill attempt without a language model.
///
/// Drills are deliberately judged by alignment rather than by asking the tutor
/// model. A drill has one right answer, checking for it is exact, and doing it this
/// way means a review takes about two seconds instead of fifteen — which is what
/// makes it realistic to get through a queue of them.
public enum DrillGrader {

    public struct Result: Equatable, Sendable {
        public let grade: DrillGrade
        public let usedFix: Bool
        public let repeatedMistake: Bool
    }

    static let shortAnswerSlack = 6

    public static func grade(
        spoken: String,
        expectedFix: String,
        originalMistake: String
    ) -> Result {
        let said = Words.normalised(spoken)
        let fix = Words.normalised(expectedFix)
        let mistake = Words.normalised(originalMistake)

        let usedFix = !fix.isEmpty && Words.containsRun(said, fix)
        let repeated = !mistake.isEmpty && Words.containsRun(said, mistake)

        let grade: DrillGrade
        if repeated {
            // Saying the mistake again is a lapse even if the fix appears too,
            // because the old habit clearly has not been replaced.
            grade = .missed
        } else if !usedFix {
            grade = .missed
        } else if said.count <= fix.count + shortAnswerSlack {
            // A short, direct answer is effortless recall; a long one usually
            // means they talked their way to it.
            grade = .easy
        } else {
            grade = .good
        }

        return Result(grade: grade, usedFix: usedFix, repeatedMistake: repeated)
    }
}
