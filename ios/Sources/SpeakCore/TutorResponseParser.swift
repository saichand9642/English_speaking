import Foundation

/// Turns the tutor model's raw output into a `TutorFeedback`.
///
/// The on-device model is constrained by a GBNF grammar, so its JSON should always be
/// well formed. This parser is still deliberately forgiving, because the optional
/// Gemini path is not grammar-constrained and small models like to wrap JSON in prose
/// or markdown fences. Parsing must never be the reason a turn fails: if the JSON is
/// unusable we treat the whole output as spoken reply with no corrections, which is
/// the safe direction to fail in — the app stays conversational and simply claims no
/// mistakes.
public enum TutorResponseParser {

    public static func parse(raw: String, transcript: String) -> TutorFeedback {
        guard let body = extractJSONObject(raw),
              let data = body.data(using: .utf8),
              let root = try? JSONSerialization.jsonObject(with: data) as? [String: Any]
        else {
            return TutorFeedback(
                transcript: transcript,
                correctedSentence: nil,
                corrections: [],
                spokenReply: cleanProse(raw)
            )
        }

        let claimed = (root["corrections"] as? [[String: Any]] ?? []).compactMap(correction(from:))
        let corrected = string(root["corrected"])
        let reply = string(root["reply"]) ?? string(root["say"]) ?? ""

        // Everything the model claims is then checked against the real transcript.
        let verified = CorrectionVerifier.verify(transcript: transcript, corrections: claimed)

        return TutorFeedback(
            transcript: transcript,
            // A "corrected sentence" is meaningless if nothing survived verification.
            correctedSentence: verified.isEmpty ? nil : corrected,
            corrections: verified,
            spokenReply: reply
        )
    }

    private static func correction(from raw: [String: Any]) -> Correction? {
        guard let wrong = string(raw["wrong"]) ?? string(raw["was"]),
              let right = string(raw["right"]) ?? string(raw["fix"]),
              !wrong.isEmpty, !right.isEmpty
        else { return nil }

        let why = string(raw["why"]) ?? string(raw["explanation"]) ?? ""
        let type = string(raw["type"]) ?? string(raw["category"])

        return Correction(
            wrong: wrong.trimmingCharacters(in: .whitespacesAndNewlines),
            right: right.trimmingCharacters(in: .whitespacesAndNewlines),
            explanation: why.trimmingCharacters(in: .whitespacesAndNewlines),
            category: MistakeCategory.from(type)
        )
    }

    private static func string(_ value: Any?) -> String? {
        guard let text = value as? String, !text.isEmpty else { return nil }
        return text
    }

    /// Finds the first balanced JSON object in the text, ignoring braces that appear
    /// inside string literals. A plain first-brace/last-brace pair breaks as soon as
    /// an explanation contains a brace.
    static func extractJSONObject(_ raw: String) -> String? {
        guard let start = raw.firstIndex(of: "{") else { return nil }
        var depth = 0
        var inString = false
        var escaped = false
        var index = start

        while index < raw.endIndex {
            let ch = raw[index]
            if inString {
                if escaped {
                    escaped = false
                } else if ch == "\\" {
                    escaped = true
                } else if ch == "\"" {
                    inString = false
                }
            } else {
                switch ch {
                case "\"": inString = true
                case "{": depth += 1
                case "}":
                    depth -= 1
                    if depth == 0 {
                        return String(raw[start...index])
                    }
                default: break
                }
            }
            index = raw.index(after: index)
        }
        return nil
    }

    /// Strips markdown fences so a fallback reply reads as speech.
    private static func cleanProse(_ raw: String) -> String {
        var text = raw
        // Remove ```json / ``` fences without touching the prose between them.
        while let fence = text.range(of: "```[a-zA-Z]*", options: .regularExpression) {
            text.removeSubrange(fence)
        }
        return text.trimmingCharacters(in: .whitespacesAndNewlines)
    }
}
