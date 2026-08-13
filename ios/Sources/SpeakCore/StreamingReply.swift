import Foundation

/// Pulls the `reply` field out of a JSON document while it is still being
/// generated, character by character.
///
/// This is what makes the tutor feel like it is talking rather than thinking. The
/// model emits roughly ten tokens a second, so waiting for the closing brace before
/// speaking costs ten to twenty seconds of silence. Because the grammar puts
/// `reply` first, its text is complete long before the corrections are, and this
/// extractor hands it over as it arrives so speech can begin almost immediately.
public final class ReplyStreamExtractor {

    private enum Phase { case seekingKey, afterKey, inValue, done }

    private var phase: Phase = .seekingKey
    private var window = ""
    private var escaped = false

    /// Long enough to span the key even if it is split across tokens.
    private static let keyWindow = 16

    public init() {}

    /// True once the reply string has been closed.
    public var isComplete: Bool { phase == .done }

    /// Feeds newly generated text and returns any reply text that became
    /// available, decoded. Possibly empty.
    public func accept(_ delta: String) -> String {
        guard phase != .done, !delta.isEmpty else { return "" }
        var out = ""

        for ch in delta {
            switch phase {
            case .seekingKey:
                window.append(ch)
                if window.count > Self.keyWindow {
                    window.removeFirst(window.count - Self.keyWindow)
                }
                if window.contains("\"reply\"") {
                    phase = .afterKey
                    window = ""
                }

            case .afterKey:
                // Skip the colon and any whitespace, then the opening quote.
                if ch == "\"" { phase = .inValue }

            case .inValue:
                if escaped {
                    out.append(Self.unescape(ch))
                    escaped = false
                } else if ch == "\\" {
                    escaped = true
                } else if ch == "\"" {
                    phase = .done
                } else {
                    out.append(ch)
                }

            case .done:
                break
            }
            if phase == .done { break }
        }
        return out
    }

    private static func unescape(_ ch: Character) -> Character {
        switch ch {
        case "n": return "\n"
        case "t": return "\t"
        case "r": return "\r"
        // A \uXXXX sequence would need four more characters; the tutor's replies
        // are plain English, so dropping the marker is safer than mis-decoding.
        case "u": return " "
        default: return ch
        }
    }

    public func reset() {
        phase = .seekingKey
        window = ""
        escaped = false
    }
}

/// Buffers streamed text and releases it one sentence at a time.
///
/// Speech synthesis sounds wrong when fed word by word — it loses sentence
/// intonation and the pauses land in the wrong places. Handing it whole sentences
/// keeps the prosody natural while still letting speech start before the model has
/// finished generating.
public final class SentenceChunker {

    private var buffer = ""
    private let softLimit: Int

    private static let closers: Set<Character> = ["\"", "'", ")", "]", "”"]

    /// - Parameter softLimit: speak a partial sentence once it gets this long, so
    ///   speech never stalls behind a full stop that is not coming.
    public init(softLimit: Int = 160) {
        self.softLimit = softLimit
    }

    /// Returns sentences that are now complete and ready to speak.
    public func accept(_ text: String) -> [String] {
        guard !text.isEmpty else { return [] }
        buffer += text

        var ready: [String] = []
        while let boundary = findBoundary() {
            let sentence = String(buffer[buffer.startIndex...boundary])
                .trimmingCharacters(in: .whitespacesAndNewlines)
            buffer.removeSubrange(buffer.startIndex...boundary)
            if !sentence.isEmpty { ready.append(sentence) }
        }

        if buffer.count >= softLimit,
           let breakAt = buffer.lastIndex(of: " ") {
            let chunk = String(buffer[buffer.startIndex..<breakAt])
                .trimmingCharacters(in: .whitespacesAndNewlines)
            buffer.removeSubrange(buffer.startIndex...breakAt)
            if !chunk.isEmpty { ready.append(chunk) }
        }
        return ready
    }

    /// Releases whatever is left once generation ends.
    public func flush() -> String {
        let remaining = buffer.trimmingCharacters(in: .whitespacesAndNewlines)
        buffer = ""
        return remaining
    }

    /// Index of the last character of a complete sentence, or nil.
    ///
    /// Two details matter. A full stop only ends a sentence when a space or line
    /// break follows, which keeps "3.5" and "Mr. Rao" in one piece. And a closing
    /// quote immediately after the stop belongs to the sentence, so
    /// `He said "hello."` is spoken whole rather than cut before its own quote.
    private func findBoundary() -> String.Index? {
        var index = buffer.startIndex
        while index < buffer.endIndex {
            let ch = buffer[index]
            // A line break is always a boundary; nothing following can change it.
            if ch == "\n" { return index }

            if ch == "." || ch == "!" || ch == "?" {
                var end = index
                var lookahead = buffer.index(after: end)
                while lookahead < buffer.endIndex, Self.closers.contains(buffer[lookahead]) {
                    end = lookahead
                    lookahead = buffer.index(after: end)
                }
                // Nothing after it yet: wait, in case this turns out to be "3.5".
                guard lookahead < buffer.endIndex else { return nil }
                let next = buffer[lookahead]
                if next == " " || next == "\n" { return end }
            }
            index = buffer.index(after: index)
        }
        return nil
    }
}
