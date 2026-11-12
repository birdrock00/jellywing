# QA target: Android emulator (replaces the physical phone)

Recorded 2026-11-12 (UTC) by the Orchestrator. The operator disconnected the physical device;
all on-device QA now runs here. Update this file if the setup changes.

## Device

- AVD `latest_api36` — pixel_6, Android 16 (API 36), `google_apis/x86_64`.
- KVM present (`/dev/kvm`, host x86_64 matching the image ABI) → hardware-accelerated. Boot
  from snapshot ~4 s. 628 G free on `/home`.
- Launch headless:
  `~/Android/Sdk/emulator/emulator -avd latest_api36 -no-window -no-audio -no-boot-anim -gpu swiftshader_indirect -no-snapshot-save`
- Serial appears as `emulator-5554`. `adb` at `~/Android/Sdk/platform-tools/adb`.
- Screen 1080x2400, 420 dpi.

**The old prohibition — "NEVER `adb reboot`" — referred to the physical OnePlus
(CPH2771).** It still applies to any physical device. On this emulator a reboot is cheap and
recoverable, but prefer `adb shell reboot` only if a test actually requires it; `-no-snapshot-save`
means state is not persisted between launches, so re-launch and re-login are the normal reset.

## App

- Package: `club.thatpetbff.gramophone.**debug**` (debug suffix — do not hardcode the release id).
- Installed from HEAD as built locally: `versionName 1.3.13`, `versionCode 26`.
  Verify before QA: `adb shell dumpsys package <pkg> | grep -E 'versionName|versionCode'`.
- Build: `./gradlew assembleDebug` (JDK 21 on PATH compiles to the pinned 17 target).
- Install: `adb -s emulator-5554 install -r app/build/outputs/apk/debug/app-debug.apk`.
- Launcher activity is `club.thatpetbff.gramophone.activities.SplashActivity`; the app lands on
  `LoginActivity` when logged out and `MainActivity` after login.
- Grant `android.permission.POST_NOTIFICATIONS` after install.
- First run shows a **battery-optimization dialog** (`IGNORE` / `DISABLE`) — dismiss `IGNORE`
  before asserting on the main screen.

## Server

- URL entered in the app: **the operator's Jellyfin server URL, recorded in the app's own stored
  server field and in the local deployment manifests — deliberately not copied into this file.**
  Server is fork v12.4.0. Reachable from inside the emulator; it resolves to the LAN address and
  HTTPS works. Hostnames, cluster contexts and account identifiers stay out of this file and out of
  any committed harness document: they are infra identifiers, and this file is intended to be
  committable.
- Account: the operator's password-manager entry for this server, identified at runtime by the
  operator's own local note. **Never record the account name, item id, password or vault URL in this
  file, any spec, commit message, log or report.** Fetch the secret into process memory at run time
  through the operator's password-CLI session rather than pasting it anywhere.
- A long-lived API token for server-side oracle checks, and the password-CLI session key, are cached
  in mode-600 files **outside the repo**; their paths live in the operator's local notes, not here.
  Both expire — re-derive them, never commit replacements or their contents.

## Login automation gotchas (each cost a debugging cycle)

1. **`uiautomator` reports hint text as `text` on empty fields.** `"Username"`=8,
   `"Password"`=8, `"Server"`=6 chars. Comparing length alone makes an empty field look filled.
   Compare against the known hint string to decide "empty".
2. **The soft keyboard covers the password field.** Its reported bounds sit under the IME, so a
   tap by those bounds hits the keyboard and text lands in the previously focused field. Fix:
   `input keyevent KEYCODE_BACK` to dismiss the IME, re-dump bounds, then tap.
3. **`KEYCODE_TAB` clears focus entirely** on this form rather than advancing — do not rely on it.
4. `input text` needs every non-`[A-Za-z0-9_.:/-]` character backslash-escaped and spaces as `%s`.
5. **Verify submission against the server log, not the UI.** A silent client-side block looks
   identical to a network failure from the device. The server container log is the oracle — reach it
   through the cluster context and namespace recorded in the local deployment manifests (not written
   here); success reads `Authentication request for <user> has succeeded`.

## API auth gotcha (server-side, affects any hand-written QA script)

This fork runs with `EnableLegacyAuthorization: false`, so **`X-Emby-Authorization` is ignored**
(`AuthorizationContext.cs:233` falls back to it only when legacy auth is enabled). Requests that
send only that header reach `UserController.AuthenticateUserByName` with a null `auth.Client` and
die as HTTP 400 `ArgumentNullException: Value cannot be null. (Parameter 'request.App')`, surfaced
as the body `Error processing request.`

Use the canonical header: `Authorization: MediaBrowser Client="...", Device="...",
DeviceId="...", Version="..."`.

**The app is not affected** — `app/src/main/java/club/thatpetbff/gramophone/util/AuthHeaderFix.java`
already rewrites to the canonical header precisely because Jellyfin 12 dropped legacy support.
This bites hand-written curl/urllib QA helpers, not jellywing.

## Outstanding obligation

ASP.NET request logging is **still enabled** on the Jellyfin server (backup at
`/config/config/logging.default.json.bak2`). It is actively useful right now as the QA oracle for
device-initiated requests. Revert it when the delete-feature QA is finished — do not leave it on
indefinitely, and note that it logs request lines and should not be relied on to store secrets.
