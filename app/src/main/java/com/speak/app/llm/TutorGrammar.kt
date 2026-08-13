package com.speak.app.llm

/**
 * GBNF grammar constraining the tutor model's output.
 *
 * A 1B model asked politely for JSON produces broken JSON often enough to matter.
 * llama.cpp can mask the sampler with a grammar, which makes malformed output
 * structurally impossible rather than merely unlikely -- the model cannot emit a
 * token that would violate the shape, so there is no error path to handle.
 *
 * The field order is deliberate and load-bearing. `reply` is generated first so
 * that the spoken answer is complete while the corrections are still being
 * produced, which lets text-to-speech start several seconds earlier than it
 * otherwise could. On a CPU-only phone that ordering is the difference between a
 * conversation and a wait.
 */
internal object TutorGrammar {

    val GBNF: String = """
root ::= "{" ws "\"reply\"" ws ":" ws string ws "," ws "\"corrected\"" ws ":" ws string ws "," ws "\"corrections\"" ws ":" ws corrections ws "}"

corrections ::= "[" ws "]" | "[" ws item (ws "," ws item)* ws "]"

item ::= "{" ws "\"wrong\"" ws ":" ws string ws "," ws "\"right\"" ws ":" ws string ws "," ws "\"why\"" ws ":" ws string ws "," ws "\"type\"" ws ":" ws category ws "}"

category ::= "\"tense\"" | "\"article\"" | "\"preposition\"" | "\"plural\"" | "\"word_order\"" | "\"word_choice\"" | "\"other\""

string ::= "\"" char* "\""

char ::= [^"\\] | "\\" escape

escape ::= ["\\/bfnrt] | "u" hex hex hex hex

hex ::= [0-9a-fA-F]

ws ::= [ \t\n]*
""".trimIndent()
}
