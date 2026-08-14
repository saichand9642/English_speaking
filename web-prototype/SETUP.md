# Speak on your iPhone — setup in 15 minutes

This is the version that runs on an iPhone. You do this once, then you just tap the
icon on your home screen.

It is a single web page. There is nothing to install from an App Store, no Mac
needed, no Apple developer account, no signing, and nothing that expires after seven
days.

**The one trade-off:** it needs an internet connection, because the listening and the
corrections happen on Google's servers rather than on the phone. If you want a
version that works in aeroplane mode, that is the Android app in this same
repository — see the main [README](../README.md).

---

## Step 1 — Get your free Google AI key (2 min)

1. Go to **https://aistudio.google.com/apikey**
2. Sign in with any Google account
3. Tap **Create API key** → **Create API key in new project**
4. Copy the key (it starts with `AIza…`) and keep it somewhere for a moment

No credit card. No payment. If Google ever asks you to enable billing, you are on the
wrong page — go back to the link above.

---

## Step 2 — Put the page online (10 min)

The microphone only works on a real `https://` address, so the file has to be hosted.
GitHub Pages does this free, forever.

1. Create a free account at **https://github.com** if you don't have one
2. Push this repository, or upload `web-prototype/index.html` to a new **public** repo
3. In the repository, go to **Settings** → **Pages** (left sidebar)
4. Under *Branch*, choose **main**, folder **/ (root)**, and click **Save**
5. Wait one or two minutes, then refresh. GitHub shows your link:

   `https://YOUR-USERNAME.github.io/YOUR-REPO/web-prototype/`

That link is your app.

> A public repo means the *code* is public. Your API key is **not** in the code — you
> type it in on your phone and it is stored only there.

---

## Step 3 — Install it on your iPhone (1 min)

1. Open your link in **Safari**. It must be Safari — Chrome on iOS cannot add a
   working app to the home screen.
2. Tap the **Share** button (the square with an arrow, at the bottom)
3. Scroll down → **Add to Home Screen** → **Add**
4. Open it from the home screen. It now runs full-screen like a normal app.

---

## Step 4 — First use

1. Paste your API key, tap **Save & start**
2. Tap the gear icon and type your first language (for example *Telugu*) — this makes
   the pronunciation tips noticeably more useful
3. Tap the microphone **once**. Allow the microphone when Safari asks — this
   happens only the first time.
4. Just talk. It works out when you have stopped speaking, replies out loud, and
   then starts listening again on its own. You never tap between sentences.
5. Tap the button again when you want to stop.

If the microphone is ever refused, fix it in **Settings → Safari → Microphone →
Allow**, then close and reopen the app.

---

## What's in it

Four tabs along the bottom.

**Talk** — the main loop, and it is hands-free. One tap starts the conversation and it
keeps going by itself: it hears when you stop talking, answers, and starts listening
again. It waits until it has finished speaking before it listens, so it never records
its own voice. You get your sentence with the wrong parts struck through and the fixes
highlighted inline, a plain-language reason for each, pronunciation notes for words
that genuinely sounded unclear, and a spoken reply that asks a follow-up question.

If nobody speaks for about nine seconds it simply keeps waiting rather than dropping
out. If the connection or the key fails, it stops the session and tells you, instead
of retrying in a loop and eating your daily quota.

**Read** — one tap, then read the sentence; it stops on its own when you finish. A
sentence chosen to exercise one specific sound (v/w, th, short and long
vowels, consonant clusters, rhythm). You read it, and every word is scored against
the target. This is the most reliable pronunciation feedback in the app, because the
sentence is known in advance, so a word coming back different is a real observation
rather than a guess.

**Drill** — one tap per answer, stopping automatically when you finish speaking.
Re-tests the mistakes *you* actually made, on an SM-2 spaced-repetition
schedule. Not random review: an error you repeat becomes due again immediately, so the
habits that keep coming back are the ones you practise most. Peeking at the answer
never counts as recall.

**Progress** — your streak, minutes spoken, speaking pace against the natural 120–150
words-per-minute band, mistakes per hundred words, filler sounds per hundred words,
your recurring mistakes ranked by frequency, and this week against last week.

Settings also has **Export** and **Import**. There is no server, so that JSON file is
the only copy of your history that survives losing the phone. Import merges rather
than overwrites.

---

## It will not invent mistakes

This matters enough to explain. Language models asked to find grammar errors
sometimes report an error that is not actually in your sentence. Two things prevent
that here:

1. Every mistake the model claims is checked against the transcript of what you
   really said. If the words it quotes are not there, the correction is thrown away
   before you ever see it.
2. A fixed set of checks catches the frequent Indian-English patterns the model tends
   to miss — *discuss about*, *since three years*, *one of my friend*, *didn't went*,
   *cousin brother*, *prepone*, and so on. These are deliberately conservative: "I am
   having two brothers" is corrected, while "I am having lunch" and "I'm having
   trouble with my laptop" are left alone, because those are correct English.

The pronunciation feedback is labelled honestly too. In **Read** it says it compared
against the sentence on screen. In **Talk** it tells you a note is based on how
clearly the word came through and may have been fine.

---

## How to actually use it every day

Fifteen minutes, and it works best in this order.

1. **Read the prompt out loud and answer in two or three sentences.** Don't plan the
   sentence first — speak it the way you would in a real conversation. Corrections
   only help if the mistake was a real one.
2. **Read the fixes, then say the corrected sentence out loud.** Saying it is what
   moves it into your speech. Reading it only moves it into your memory.
3. **Do the Drill tab whenever it has anything due.** This is the part that actually
   changes habits.
4. **Once a week, open Progress** and look at your recurring mistakes. Try to use
   those exact structures correctly on purpose while you talk.

The recurring mistakes list is the real product. Almost everyone cycles through the
same five to ten errors, and fixing those is most of the improvement available.

---

## If something goes wrong

**"I couldn't get microphone access"** — Settings → Safari → Microphone → Allow. Then
close and reopen the app.

**"That API key was rejected"** — Tap the gear icon and paste it again, with no spaces
before or after.

**"You've hit today's free limit"** — The free tier resets. Wait a few minutes or come
back tomorrow. Normal daily practice will not get near the limit.

**"Google's servers are busy right now"** or *"this model is experiencing high demand"* —
Nothing is wrong with your key, your phone, or your speaking. Google's free tier runs
out of capacity in bursts, usually at peak hours. The app already retries twice and
then tries a different model before giving up, so if you still see this, the outage is
lasting more than about five seconds. Wait a minute and tap again, or set a different
model in Settings.

**Nothing happens when you tap record** — You opened the file directly instead of the
`https://` link. The microphone needs the hosted address.

**It stops listening after every sentence** — That is the old behaviour. Reload the
page so Safari picks up the current version; iOS caches home-screen apps aggressively.

**It seems to hear itself** — It waits for its own speech to finish before listening,
but a loud speaker in a small room can still bleed through. Lower the volume or use
earphones.

**No sound comes back** — iOS silences speech until you have interacted with the page.
Tap something once, then try again. Also check the ringer switch.

**The model name is rejected** — Google renames models occasionally. The app already
falls back through several names automatically; if it still fails, put a current model
name in Settings.
