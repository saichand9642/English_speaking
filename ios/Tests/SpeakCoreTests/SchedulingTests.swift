import Testing
@testable import SpeakCore

@Suite("SM-2 scheduler")
struct Sm2SchedulerTests {

    private let today: Int64 = 20_000

    @Test("a new item is due immediately")
    func newItemIsDue() {
        let state = Sm2Scheduler.initial(todayEpochDay: today)
        #expect(Sm2Scheduler.isDue(state, todayEpochDay: today))
        #expect(state.repetitions == 0)
        #expect(state.easiness == ReviewState.initialEasiness)
    }

    @Test("first success schedules one day out")
    func firstSuccess() {
        let next = Sm2Scheduler.schedule(
            state: Sm2Scheduler.initial(todayEpochDay: today),
            grade: .good,
            todayEpochDay: today
        )
        #expect(next.repetitions == 1)
        #expect(next.intervalDays == 1)
        #expect(next.dueEpochDay == today + 1)
    }

    @Test("second success schedules six days out")
    func secondSuccess() {
        var state = Sm2Scheduler.initial(todayEpochDay: today)
        state = Sm2Scheduler.schedule(state: state, grade: .good, todayEpochDay: today)
        state = Sm2Scheduler.schedule(state: state, grade: .good, todayEpochDay: today + 1)
        #expect(state.repetitions == 2)
        #expect(state.intervalDays == 6)
        #expect(state.dueEpochDay == today + 7)
    }

    @Test("later intervals multiply by easiness")
    func laterIntervals() {
        var state = Sm2Scheduler.initial(todayEpochDay: today)
        state = Sm2Scheduler.schedule(state: state, grade: .good, todayEpochDay: today)
        state = Sm2Scheduler.schedule(state: state, grade: .good, todayEpochDay: today + 1)
        let before = state.intervalDays
        state = Sm2Scheduler.schedule(state: state, grade: .good, todayEpochDay: today + 7)
        #expect(state.repetitions == 3)
        #expect(state.intervalDays > before)
    }

    @Test("intervals always move forward even at the easiness floor")
    func alwaysMovesForward() {
        var state = ReviewState(
            repetitions: 5, intervalDays: 3, easiness: 1.3, dueEpochDay: today
        )
        state = Sm2Scheduler.schedule(state: state, grade: .hesitant, todayEpochDay: today)
        #expect(state.intervalDays > 3)
    }

    @Test("a lapse resets repetitions but keeps hard-won easiness")
    func lapseKeepsEasiness() {
        var state = Sm2Scheduler.initial(todayEpochDay: today)
        for _ in 0..<3 {
            state = Sm2Scheduler.schedule(state: state, grade: .good, todayEpochDay: today)
        }
        let earned = state.easiness

        state = Sm2Scheduler.schedule(state: state, grade: .missed, todayEpochDay: today + 30)

        #expect(state.repetitions == 0)
        #expect(state.intervalDays == 1)
        #expect(state.dueEpochDay == today + 31)
        // Easiness drops, but the item does not forget that it has been hard.
        #expect(state.easiness < earned)
        #expect(state.easiness >= 1.3)
    }

    @Test("easiness never falls below the floor however often it is missed")
    func easinessFloor() {
        var state = Sm2Scheduler.initial(todayEpochDay: today)
        for offset in 0..<20 {
            state = Sm2Scheduler.schedule(
                state: state, grade: .missed, todayEpochDay: today + Int64(offset)
            )
        }
        #expect(state.easiness >= 1.3)
    }

    @Test("easy answers grow easiness faster than merely good ones")
    func easyBeatsGood() {
        let good = Sm2Scheduler.schedule(
            state: Sm2Scheduler.initial(todayEpochDay: today), grade: .good, todayEpochDay: today
        )
        let easy = Sm2Scheduler.schedule(
            state: Sm2Scheduler.initial(todayEpochDay: today), grade: .easy, todayEpochDay: today
        )
        #expect(easy.easiness > good.easiness)
    }

    @Test("hesitant still counts as a pass")
    func hesitantPasses() {
        #expect(DrillGrade.hesitant.isPass)
        #expect(!DrillGrade.missed.isPass)
        let state = Sm2Scheduler.schedule(
            state: Sm2Scheduler.initial(todayEpochDay: today),
            grade: .hesitant,
            todayEpochDay: today
        )
        #expect(state.repetitions == 1)
    }

    @Test("interval is capped so items never disappear for years")
    func intervalCap() {
        var state = Sm2Scheduler.initial(todayEpochDay: today)
        for _ in 0..<30 {
            state = Sm2Scheduler.schedule(state: state, grade: .easy, todayEpochDay: today)
        }
        #expect(state.intervalDays <= 365)
    }

    @Test("not due before its date")
    func notDueEarly() {
        let state = Sm2Scheduler.schedule(
            state: Sm2Scheduler.initial(todayEpochDay: today), grade: .good, todayEpochDay: today
        )
        #expect(!Sm2Scheduler.isDue(state, todayEpochDay: today))
        #expect(Sm2Scheduler.isDue(state, todayEpochDay: today + 1))
    }
}

@Suite("Drill grader")
struct DrillGraderTests {

    @Test("using the fix passes")
    func usingFixPasses() {
        let result = DrillGrader.grade(
            spoken: "Yesterday I went to the market",
            expectedFix: "I went",
            originalMistake: "I go"
        )
        #expect(result.usedFix)
        #expect(!result.repeatedMistake)
        #expect(result.grade.isPass)
    }

    @Test("repeating the old mistake fails even if the fix is also present")
    func repeatedMistakeFails() {
        // Saying both means the habit has not been replaced yet.
        let result = DrillGrader.grade(
            spoken: "I go yesterday, sorry, I went yesterday",
            expectedFix: "I went",
            originalMistake: "I go"
        )
        #expect(result.usedFix)
        #expect(result.repeatedMistake)
        #expect(result.grade == .missed)
    }

    @Test("missing the fix fails")
    func missingFixFails() {
        let result = DrillGrader.grade(
            spoken: "I visited the market",
            expectedFix: "I went",
            originalMistake: "I go"
        )
        #expect(!result.usedFix)
        #expect(result.grade == .missed)
    }

    @Test("a short direct answer is graded easy")
    func shortAnswerIsEasy() {
        let result = DrillGrader.grade(
            spoken: "I went to the market",
            expectedFix: "I went",
            originalMistake: "I go"
        )
        #expect(result.grade == .easy)
    }

    @Test("a long rambling answer that gets there is graded good")
    func longAnswerIsGood() {
        let result = DrillGrader.grade(
            spoken: "Well let me think about it, so the thing is that actually "
                + "in the end I went to the market with my brother",
            expectedFix: "I went",
            originalMistake: "I go"
        )
        #expect(result.grade == .good)
    }

    @Test("matching ignores case and punctuation")
    func ignoresCase() {
        let result = DrillGrader.grade(
            spoken: "Yesterday, I WENT!",
            expectedFix: "i went",
            originalMistake: "i go"
        )
        #expect(result.usedFix)
    }

    @Test("empty speech fails")
    func emptySpeechFails() {
        let result = DrillGrader.grade(spoken: "", expectedFix: "I went", originalMistake: "I go")
        #expect(result.grade == .missed)
    }
}
