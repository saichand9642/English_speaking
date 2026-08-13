# Getting Speak onto an iPhone

Read this before spending time on the iOS route. The build is the easy part; getting
a self-built app to *stay* installed on an iPhone is the hard part, and it cannot be
made free and effortless at the same time.

## First, the thing people try that cannot work

**An Android `.apk` cannot be installed on an iPhone or iPad.** Not with a converter,
not with an emulator, not with a profile. An APK contains Dalvik bytecode
(`classes.dex`) and Linux ELF libraries; iOS loads only Mach-O binaries signed by
Apple, and there is no Android runtime on iOS to execute the bytecode in. Beyond the
file format, every API the Android app uses — `AudioRecord`, `TextToSpeech`, Room,
Jetpack Compose — has no counterpart on iOS.

Anything advertising itself as an "APK to IPA converter" is a scam, adware, or a
credential harvester. There is no technical route for such a tool to work.

## The signing wall

Apple requires every app on a device to be signed. What you get depends on what you
pay:

| | Free Apple ID | Apple Developer Program |
|---|---|---|
| Cost | £0 | **$99 / year** |
| App keeps working for | **7 days**, then stops launching | 1 year |
| Apps at once | 3 | unlimited |
| Needs a Mac to sign? | Normally yes (Xcode) | No — the web portal is enough |

So the choice is:

- **Free:** re-install the app every 7 days, from a computer, over USB.
- **$99/year:** install once and forget about it, and you can do the whole thing
  from Linux via Apple's developer website.

There is no third option. This is why the Android version of Speak satisfies "zero
cost, offline, sideloadable" and the iOS version cannot.

### One exception worth checking

If your iPhone runs **iOS 16.6.1 or older**, [TrollStore](https://github.com/opa334/TrollStore)
installs unsigned apps *permanently* using a CoreTrust flaw, and the whole 7-day
problem disappears at no cost. It does **not** work on iOS 17.7+ or 18+. Check
**Settings → General → About → Software Version** before assuming either way.

## Building without a Mac

You do not need a Mac to compile. You need one only to sign, and even that can be
skipped with the tools below.

The `.github/workflows/ios.yml` workflow builds on GitHub's macOS runners, which are
**free for public repositories**. Push a commit, open the **Actions** tab, and
download the `speak-unsigned-ipa` artifact.

That `.ipa` is unsigned. It will not install as-is; it needs a signature attached.

## Signing and installing from Linux

`AltServer-Linux` signs an `.ipa` with your own free Apple ID and installs it over
USB. Roughly:

```bash
# 1. Install the USB bridge (needs admin on the machine you plug the phone into)
sudo apt install usbmuxd libimobiledevice6 libimobiledevice-utils ideviceinstaller

# 2. Confirm the phone is visible
idevice_id -l

# 3. Fetch AltServer-Linux from its releases page, then:
./AltServer -u <device-udid> -a <your-apple-id> -p <app-specific-password> Speak-unsigned.ipa
```

Then on the iPhone: **Settings → General → VPN & Device Management → Developer App →
Trust**.

Notes that matter:

- This machine does not currently have `libimobiledevice` installed and has no sudo
  access, so step 1 has to happen somewhere you are an administrator.
- Use an **app-specific password** from <https://account.apple.com>, not your real
  Apple ID password.
- Repeat every 7 days. `SideStore` can refresh the certificate over Wi-Fi instead,
  which removes the cable but not the schedule.

## Paying the $99, if you decide to

With a paid membership you can do everything from Linux and never touch Xcode:

1. At <https://developer.apple.com/account>, create a **Certificate** (upload a CSR
   generated with `openssl`), register your device's UDID, and create an **App Store
   Distribution** or **Ad Hoc** provisioning profile.
2. Add the certificate, its password, and the profile as GitHub repository secrets.
3. Extend the `app` job in `ios/../.github/workflows/ios.yml` to import them and drop
   `CODE_SIGNING_ALLOWED=NO`.

The profile then lasts a year, and the app simply stays installed.

## What is actually built so far

| Piece | State |
|---|---|
| `SpeakCore` — corrections, SM-2, pronunciation, rhythm, streaming parser | **Done. 132 tests, passing on Linux and macOS in CI.** |
| SwiftUI screens, AVAudioEngine capture, speech synthesis | Not yet written |
| whisper.cpp / llama.cpp xcframeworks with Metal | Not yet written |
| Unsigned `.ipa` CI job | Written, skipped until the app sources land |

The reasoning core — the part that decides what counts as a mistake, refuses to
invent errors, schedules your drills, and measures your rhythm — is finished and
tested. It is shared logic, so it does not need rewriting again if the app layer
changes.

## The honest recommendation

You have an Android phone. The Android app is finished, verified, fully offline, and
free forever, which is exactly what you originally asked for. Install that and start
practising today.

Treat iOS as a second target, and decide the signing question first — because whether
you are willing to re-install weekly or pay $99/year determines whether the offline
iOS app is worth finishing at all. If neither appeals, the browser version in
`web-prototype/` runs on your iPhone in about fifteen minutes with no signing, no
Mac, and no expiry, at the cost of needing an internet connection.
