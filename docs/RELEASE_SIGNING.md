# Signing a release APK

You only need this if you want a **release** build. Debug APKs are signed
automatically with a throwaway debug key and install fine by sideloading, which is
all most people need.

A release build matters for two reasons: it is smaller and faster (R8 shrinking is
on), and Android will let you *upgrade* an installed release build in place only if
the new APK is signed with the same key. Lose the key and your only option is to
uninstall, which deletes your practice history unless you exported it first.

## 1. Create a keystore, once

Run this on your own machine. Nothing here ever gets committed.

```bash
keytool -genkeypair \
  -alias speak \
  -keyalg RSA \
  -keysize 4096 \
  -validity 10000 \
  -keystore speak-release.jks
```

It asks for a keystore password, then some identity fields you can answer however
you like (`CN=Speak`, and blanks are fine). When it offers to reuse the keystore
password for the key itself, say yes — one less thing to lose.

Keep `speak-release.jks` and its password somewhere you will still have them in two
years. A password manager, not the repository. `*.jks` is in `.gitignore`, so git
will not pick it up by accident, but nothing stops you copying it somewhere public
by hand — don't.

## 2. Build locally

Point the build at the keystore with environment variables. The build reads only
these; there is no `keystore.properties` file to leak.

```bash
export SPEAK_KEYSTORE_PATH=/absolute/path/to/speak-release.jks
export SPEAK_KEYSTORE_PASSWORD='your keystore password'
export SPEAK_KEY_ALIAS=speak
export SPEAK_KEY_PASSWORD='your key password'

./gradlew assembleRelease
```

The APK lands in `app/build/outputs/apk/release/`.

If `SPEAK_KEYSTORE_PATH` is unset or points at a missing file, no release signing
config is created at all and `assembleRelease` produces an unsigned APK. That is
deliberate: a missing key should not silently sign your release with a debug
certificate.

## 3. Build on GitHub Actions

Add four repository secrets under **Settings → Secrets and variables → Actions →
New repository secret**:

| Secret | Value |
|---|---|
| `SPEAK_KEYSTORE_BASE64` | the keystore file, base64 encoded (below) |
| `SPEAK_KEYSTORE_PASSWORD` | your keystore password |
| `SPEAK_KEY_ALIAS` | `speak` |
| `SPEAK_KEY_PASSWORD` | your key password |

Encode the keystore:

```bash
base64 -w 0 speak-release.jks > speak-release.jks.base64
```

Then paste the contents of that file as the secret value. Delete the `.base64` file
afterwards — it is exactly as sensitive as the keystore itself.

Now push a version tag:

```bash
git tag v1.0.0
git push origin v1.0.0
```

The `release` job decodes the keystore into the runner's temp directory (outside the
workspace, so it cannot end up in an artifact), builds, deletes the keystore, and
attaches the signed APK to a GitHub release.

If you have not set `SPEAK_KEYSTORE_BASE64`, the job logs a warning and skips
signing rather than failing the build. Debug APKs keep being produced on every
push regardless.

## Keeping your history across reinstalls

Signing keys are about upgrades, not backups. Before you reinstall or switch
signing keys, open **Settings → Your history → Export to a file** and keep the
JSON somewhere safe. There is no server, so that file is the only copy.
