import Foundation

/// Builds the prompt for the on-device tutor, in Gemma 3's turn format.
///
/// Gemma has no system role, so the instructions live in the first user turn. One
/// worked example follows as a user/model pair. A single example roughly doubles the
/// reliability of a 1B model's output shape, and two barely improve on one, so one is
/// what we pay for — every extra token here is prefill time the learner waits
/// through.
public enum TutorPrompt {

    public struct Exchange: Sendable {
        public let student: String
        public let tutor: String

        public init(student: String, tutor: String) {
            self.student = student
            self.tutor = tutor
        }
    }

    /// Turns kept from earlier in the conversation. Kept small to bound prefill.
    static let historyTurns = 2

    static let instructions = """
    You are a warm, patient English speaking tutor. Your student is an adult Indian \
    English speaker practising spoken conversation.

    Answer with JSON only, in this exact shape:
    {"reply": "...", "corrected": "...", "corrections": [{"wrong": "...", "right": "...", \
    "why": "...", "type": "..."}]}

    Rules:
    - "reply": one or two warm sentences reacting to what the student said, then one \
    question to keep them talking. Never mention mistakes or grammar here.
    - "corrected": the student's sentence written correctly. If it was already correct, \
    copy it unchanged.
    - "corrections": one entry for each real mistake. Copy "wrong" exactly as the \
    student said it. "why" is one short sentence in plain words, with no grammar \
    jargon. "type" is one of: tense, article, preposition, plural, word_order, \
    word_choice, other.
    - If the sentence has no mistakes, "corrections" must be [].
    - Never invent a mistake. Only quote words the student actually said.
    """

    static let exampleStudent = """
    Topic: weekends
    Student said: "Yesterday I go to market and buy two kilo rice."
    """

    static let exampleModel = #"""
    {"reply": "That sounds like a useful trip to the market. What did you cook with the rice?", "corrected": "Yesterday I went to the market and bought two kilos of rice.", "corrections": [{"wrong": "I go", "right": "I went", "why": "This happened yesterday, so the verb changes to its past form.", "type": "tense"}, {"wrong": "buy", "right": "bought", "why": "The second action was also yesterday, so it needs the past form too.", "type": "tense"}, {"wrong": "two kilo", "right": "two kilos of", "why": "More than one kilo takes an s on the end.", "type": "plural"}]}
    """#

    public static func build(
        topic: String?,
        history: [Exchange],
        studentSentence: String
    ) -> String {
        var prompt = ""
        prompt += "<start_of_turn>user\n"
        prompt += instructions
        prompt += "\n\n"
        prompt += exampleStudent
        prompt += "<end_of_turn>\n"
        prompt += "<start_of_turn>model\n"
        prompt += exampleModel
        prompt += "<end_of_turn>\n"

        for exchange in history.suffix(historyTurns) {
            prompt += "<start_of_turn>user\n"
            prompt += "Student said: \"" + String(exchange.student.trimmed().prefix(300)) + "\""
            prompt += "<end_of_turn>\n"
            prompt += "<start_of_turn>model\n"
            // Only the spoken half of an earlier turn is worth replaying: the
            // corrections were already delivered and would waste prefill.
            prompt += "{\"reply\": \"" + escape(String(exchange.tutor.trimmed().prefix(300)))
            prompt += "\", \"corrected\": \"\", \"corrections\": []}"
            prompt += "<end_of_turn>\n"
        }

        prompt += "<start_of_turn>user\n"
        if let topic, !topic.trimmed().isEmpty {
            prompt += "Topic: " + topic.trimmed() + "\n"
        }
        prompt += "Student said: \"" + String(studentSentence.trimmed().prefix(500)) + "\""
        prompt += "<end_of_turn>\n"
        prompt += "<start_of_turn>model\n"
        return prompt
    }

    /// Minimal JSON string escaping for text embedded in the replayed history.
    private static func escape(_ text: String) -> String {
        text.replacingOccurrences(of: "\\", with: "\\\\")
            .replacingOccurrences(of: "\"", with: "\\\"")
            .replacingOccurrences(of: "\n", with: " ")
    }
}

/// GBNF grammar constraining the tutor model's output.
///
/// A 1B model asked politely for JSON produces broken JSON often enough to matter.
/// llama.cpp can mask the sampler with a grammar, which makes malformed output
/// structurally impossible rather than merely unlikely — the model cannot emit a
/// token that would violate the shape, so there is no error path to handle.
///
/// The field order is deliberate and load-bearing. `reply` is generated first so that
/// the spoken answer is complete while the corrections are still being produced,
/// which lets speech start several seconds earlier than it otherwise could.
public enum TutorGrammar {
    public static let gbnf = #"""
    root ::= "{" ws "\"reply\"" ws ":" ws string ws "," ws "\"corrected\"" ws ":" ws string ws "," ws "\"corrections\"" ws ":" ws corrections ws "}"

    corrections ::= "[" ws "]" | "[" ws item (ws "," ws item)* ws "]"

    item ::= "{" ws "\"wrong\"" ws ":" ws string ws "," ws "\"right\"" ws ":" ws string ws "," ws "\"why\"" ws ":" ws string ws "," ws "\"type\"" ws ":" ws category ws "}"

    category ::= "\"tense\"" | "\"article\"" | "\"preposition\"" | "\"plural\"" | "\"word_order\"" | "\"word_choice\"" | "\"other\""

    string ::= "\"" char* "\""

    char ::= [^"\\] | "\\" escape

    escape ::= ["\\/bfnrt] | "u" hex hex hex hex

    hex ::= [0-9a-fA-F]

    ws ::= [ \t\n]*
    """#
}

extension String {
    func trimmed() -> String {
        trimmingCharacters(in: .whitespacesAndNewlines)
    }
}
