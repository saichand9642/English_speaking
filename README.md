# Speak

An Android app that teaches spoken English by having a real voice conversation with
you and correcting your mistakes. Everything runs on the phone: speech recognition,
the tutor, and the voice. No account, no server, no analytics, and no cost, ever.

Put the phone in aeroplane mode and it still listens, understands, corrects, and
talks back.

---

## What it does

**Talk.** Tap the microphone once and speak. It notices when you have stopped
talking, transcribes what you actually said — errors intact, never quietly tidied
up — and replies out loud like a patient tutor, asking a follow-up question to keep
you going. On screen you get your own sentence with the wrong parts struck through
and the fixes highlighted inline.

**Read aloud.** It shows a sentence chosen to exercise one specific sound, you read
it, and every word is scored against the target. This is where pronunciation
feedback is genuinely reliable (see [Honest limitations](#honest-limitations)).

**Drill.** It re-tests the mistakes *you* personally made, on an SM-2 spaced
repetition schedule. Not random review — the errors you repeat come back just as you
are about to forget them again.

**Progress.** Every mistake is logged with a timestamp. There is a "mistakes you
keep making" list ranked by frequency, trends for speaking pace, filler words and
mistakes per hundred words, and a weekly summary. No confetti, no streak guilt — a
streak that dies at midnight turns a learning tool into a source of anxiety, so
yesterday still counts as alive.

For every mistake you get the exact wrong fragment, the fix, a one-sentence reason
in plain words with no grammar jargon, and a category (tense, article, preposition,
plural, word order, word choice).

---

## Honest limitations

Read this before you use it. Three things are worth knowing up front.

### It is not instant

The tutor is a 1-billion-parameter model running on your phone's CPU. On a mid-range
6 GB phone with no NPU, expect roughly:

| Step | Time |
|---|---|
| Transcribing 10 seconds of speech (whisper tiny.en) | 1.5–3.5 s |
| Tutor thinking (Gemma 3 1B, int4) | 6–12 s |
| **Tap to first spoken word** | **≈ 9–13 s** |

Two things are done specifically to hide this. The tutor's reply is generated
*before* its corrections and streamed into speech sentence by sentence, so it starts
talking while it is still working out what to correct. And read-aloud and drill modes
skip the language model entirely — they score by alignment, so they respond in about
two seconds.

It is still not a phone call. If you expect the pace of talking to a person, you will
be disappointed. If you accept a thoughtful pause, it works well.

### Pronunciation feedback is strong in one mode and suggestive in the other

True per-phoneme scoring needs an acoustic model that exposes phoneme
probabilities. whisper.cpp does not, and no free offline Android package does, so
this app does not pretend to do it.

- **In Read aloud**, the target sentence is known, so a word that comes back
  different is a directly observed substitution. This is where v/w and th detection
  actually works, matched against a table of Indian-English patterns.
- **In free conversation**, there is no target. The only signal is how confident the
  recogniser was about each word, so notes are marked *"based on how clearly this
  word came through — it may have been fine"* and worded as observations, never
  verdicts.

Rhythm is measured properly from the waveform in both modes: syllable nuclei are
detected from the energy envelope and the variability of the gaps between them is
reported, which is what distinguishes syllable-timed from stress-timed speech.

### A small model can invent mistakes, so it is checked

Gemma 3 1B is not reliable enough to be trusted on its own. Three things guard
against it inventing errors you did not make:

1. Its output shape is fixed by a GBNF grammar, so malformed JSON is impossible.
2. Every correction it claims is checked against your actual transcript, and
   discarded if the words it quotes were never spoken.
3. A deterministic rule checker catches the high-frequency Indian-English patterns
   the model tends to miss — and is deliberately conservative, so `I am having
   lunch` is left alone while `I am having two brothers` is corrected.

It is much safer than a bare model. It is not perfect.

---

## Minimum phone

| | |
|---|---|
| Android | 9.0 (API 28) or newer |
| CPU | 64-bit ARM (`arm64-v8a`) — every phone with 6 GB of RAM qualifies |
| RAM | 4 GB works; 6 GB recommended |
| Free storage | about 1.1 GB (APK, plus a 769 MB one-time model download) |
| Microphone | required |
| Internet | for the one-time model download only |

32-bit ARM is not supported: a 769 MB model does not fit sensibly in a 32-bit
address space. To run on an x86 emulator, add `"x86_64"` to `abiFilters` in
`app/build.gradle.kts`.

---

## Installing the APK

You do not need the Play Store or a developer account.

### From GitHub Actions (no laptop setup needed)

1. Push this repository to GitHub. Every push builds an APK.
2. Open the **Actions** tab, click the most recent run, and wait for it to finish.
3. Download the **speak-debug-apk** artifact and unzip it.
4. Copy the `.apk` to your phone, tap it, and allow installing from unknown sources
   when Android asks.

### From your own machine

```bash
./gradlew assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Or copy `app/build/outputs/apk/debug/app-debug.apk` to the phone by hand.

For a smaller, signed, upgradeable build, see [docs/RELEASE_SIGNING.md](docs/RELEASE_SIGNING.md).

### First launch

1. It explains why it needs the microphone, then asks for it.
2. Speech recognition already works — the speech model ships inside the APK.
3. To get grammar corrections, open **Settings → Tutor model → Download** (769 MB,
   once). The download resumes if it is interrupted, and is checksum-verified.
4. If no offline voice is installed, it says so and offers to open Android's voice
   installer. Do that, or it can correct you but not speak to you.

Until the tutor model is downloaded the app still records, transcribes and gives
pronunciation and fluency feedback. It just cannot correct grammar yet.

---

## Building

Requirements: JDK 17, the Android SDK, and a network connection the first time.
`./gradlew assembleDebug` on a clean checkout does everything else itself:

- downloads the Android NDK 27.3 and CMake 3.31.6 if missing
- fetches and compiles `whisper.cpp` v1.9.2 and `llama.cpp` b10398 for `arm64-v8a`
- downloads the 31 MB Whisper model into `assets/` and verifies its SHA-256

The first build takes about 5 minutes because the native libraries compile from
source. Later builds take seconds.

```bash
./gradlew assembleDebug      # debug APK
./gradlew testDebugUnitTest  # 112 unit tests
./gradlew assembleRelease    # signed release, see docs/RELEASE_SIGNING.md
```

No model file and no key is ever committed. `.gitignore` covers `*.bin`, `*.gguf`,
`*.jks` and `*.keystore`.

---

## Architecture

```
app/src/main/
├── cpp/            JNI: one .so containing whisper + llama, sharing one ggml
├── java/com/speak/app/
│   ├── audio/      microphone, silence detection, text-to-speech
│   ├── stt/         whisper.cpp bridge, per-word confidence parsing
│   ├── llm/         llama.cpp bridge, GBNF grammar, prompt, Gemini (optional)
│   ├── domain/      correction, pronunciation, srs, fluency, tutor  (no Android deps)
│   ├── data/        Room database, settings, backup, model download, content
│   ├── ui/          Compose screens and view models
│   └── di/          hand-wired dependency container
└── src/test/       unit tests for the domain and audio layers
```

Kotlin, Jetpack Compose, Material 3, MVVM with coroutines and Flow. Room for
storage. No Firebase, no analytics, no crash reporter, no ads, no telemetry.

### Notes on two decisions worth explaining

**Why llama.cpp and not MediaPipe.** The spec asked for MediaPipe's LLM Inference
API. Google gates the `gemma3-1b-it-int4.task` file behind a Hugging Face licence
acceptance — an anonymous request gets **HTTP 401** — so a first-launch download
cannot fetch it without an account and a token. llama.cpp with an ungated GGUF gives
the same offline result with no account.

**How whisper.cpp and llama.cpp coexist.** Both vendor their own copy of `ggml`, so
adding both naively collides on CMake targets and on symbols at load time. Both,
however, guard their copy with `if (NOT TARGET ggml)`, and llama.cpp's CMakeLists
says explicitly *"otherwise assume ggml is added by a parent CMakeLists.txt"*. So
llama.cpp is added first and whisper reuses its (newer, Gemma-3-capable) ggml.
whisper only includes ggml's public headers, so it builds against it happily. A
linker version script then exports nothing but the JNI entry points, which takes the
stripped library from 56 MB to 4.2 MB.

---

## The models

| File | Size | Where it comes from | Licence |
|---|---|---|---|
| `ggml-tiny.en-q5_1.bin` | 31 MB | fetched at build time, bundled in the APK | MIT |
| `gemma-3-1b-it-Q4_K_M.gguf` | 769 MB | downloaded on first launch from `ggml-org` | [Gemma Terms](https://ai.google.dev/gemma/terms) |

Both are checksum-verified. Neither is in this repository.

---

## Privacy

Your audio is transcribed on the phone and never written to disk. The database never
leaves the device. There is no account and no server.

The one exception is opt-in and off by default: if you paste a free Google AI Studio
key into Settings *and* enable it, your transcripts are sent to Google's Gemini API
for sharper corrections while you are online. The app is fully usable having never
entered a key, no key is bundled, and the key is stored in
`EncryptedSharedPreferences` and excluded from backup exports.

Because there is no server, **Settings → Your history → Export** is the only way your
history survives losing the phone. Import merges rather than overwrites.

---

## Licence

MIT — see [LICENSE](LICENSE). Bundled model files carry their own terms, listed above.

The `web-prototype/` directory holds an earlier browser-based version of this idea,
kept for reference. It required an API key and a connection; this app does not.
