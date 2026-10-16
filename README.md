# Managed Hotspot / Hotspot Keeper

An Android app for managing the phone's existing Wi-Fi hotspot: choose known devices, duration and a reported-data allowance, then start it. Built for Pixel and Android 16+, with a Shizuku backend for restricted system operations.

## Install and use

1. Install the APK over the earlier Hotspot Keeper build. The supplied 0.2.2 APK uses the same development signing certificate, so saved settings are preserved.
2. Start Shizuku, open Keeper, and tap **Connect / authorise Shizuku**. Approve only this app's request.
3. Allow notifications and turn on **Prompt whenever hotspot is enabled**. This arms an ongoing foreground watcher.
4. Enable the hotspot from Android's Quick Settings or Settings. Keeper posts **Hotspot enabled · set session rules** within roughly one polling interval (normally three seconds). Tap it to choose rules. Android does not permit us to reliably force the editor onto the screen.
5. First use password access to connect your devices. Tap **Remember connected devices**, then long-press each to name it. You can also enter a device's Wi-Fi MAC address manually.
6. Choose **Only selected devices**, duration in minutes and allowance in MB. Blank/zero means no additional limit. **Apply rules** updates a running hotspot; **Start hotspot** configures the selected rules before requesting startup.
7. Use the ongoing notification to reopen rules or stop the hotspot. After a reboot, restart Shizuku. Keeper attempts to rearm its watcher after boot, but Android force-stop requires opening Keeper again.

## Implemented behavior

- Uses the existing saved hotspot SSID/password/security. Does not invent a replacement network or bypass carrier entitlement checks.
- Session notification for externally started hotspots. While rules are pending, Android's existing password access remains in effect: opening the hotspot externally is not a pre-approval barrier.
- System-enforced MAC allowlist when the device reports support. Unknown devices are rejected by Android's hotspot rather than repeatedly disconnected by app polling. Friendly names are local labels, not identity evidence.
- Live rule changes supported by Android's hotspot configuration update API. Leaving selected-only mode temporarily removes device blocks; existing device access settings are restored when the session ends.
- Timer and data thresholds request hotspot shutdown in a Shizuku daemon, independent of the activity being open. An ongoing app watcher reports actual state and errors. Stop requests are not labelled as successful until Android reports the hotspot off.
- Live connected-device reporting and local enrolment. Private/randomised MAC addresses can change; re-enrol or configure a stable private address on the client when needed.
- No timer leaves the existing idle-shutdown setting unchanged. Editing a timer restarts its countdown from the edit time. Editing the allowance uses the original session baseline.
- Data cap enabled only if Android supplies tethering counters. Refresh is requested, then counters are checked about every five seconds. Counters may lag, combine Wi-Fi/USB/Bluetooth tethering, or omit unsupported/offloaded traffic. Overshoot is possible. This is not an exact byte firewall or guaranteed billing limit. If a previously usable meter fails during a capped session, shutdown is requested.
- Device settings are restored only if the current device rules still match Keeper's applied rules. SSID/password changes made elsewhere are preserved. A minimal shell-owned recovery file contains device-rule lists, never hotspot credentials. If the controller restarts with unfinished owned rules, it ends the old session before restoring them.
- Loss of Shizuku access produces an attention notification. Applied device rules may remain in Android, but automatic cutoff cannot be guaranteed without the controller. The app never falls back to SIM-wide data totals or hidden-SSID access control.

## Permissions and trust

Shizuku authorisation gives the backend shell-level access; this is broader than a hotspot-specific Android permission. Only the user-service executes restricted APIs. Its Binder commands validate the calling UID and accept narrowly defined session commands—no arbitrary shell command execution. No root, bootloader unlock, Internet permission, analytics, account or cloud backend is used.

Notifications and a foreground service are needed for automatic prompts. An opt-in boot receiver rearms the watcher after boot. The backend uses Shizuku's network-stat access; no separate usage-access permission is requested.

Timed/capped sessions keep the CPU awake through a shell-owned partial wake lock so the deadline monitor can run with the screen locked. The lock is released when the session ends or those limits are removed; it uses additional battery. The watcher polls while armed and therefore uses some battery. A three-second interval is a compromise for this prototype; event-only background detection is not guaranteed by Pixel's public APIs. Disabling the watcher stops prompts, not an already configured session's daemon enforcement. Stop the hotspot to end that session.

## Build

JDK 17, Android SDK platform 36 and Build Tools 36.0.0 are required.

```bash
scripts/build.sh /absolute/path/to/android-sdk
```

The SDK-only build compiles Java, dexes the pinned Shizuku libraries, creates/signs the APK and runs the platform-independent rule/policy checks. It downloads checksum-pinned upstream AARs through `scripts/fetch-deps.py`. Output: `dist/HotspotKeeper-debug.apk`.

Android Studio / Gradle is also supported (AGP 8.10.1, Gradle 8.11.1, SDK 36), using Maven Shizuku dependencies. A Gradle wrapper is not yet bundled. Use `gradle :app:assembleDebug`.

GitHub Actions builds on source changes and uploads the APK. Set repository secret `DEBUG_KEYSTORE_BASE64` to reuse a trusted development certificate across CI builds. Without it, CI creates a fresh development key and that artifact cannot update differently signed installations. Keystores are deliberately not committed. The manually supplied APK preserves the earlier local certificate.

## Architecture

- `MainActivity`: onboarding, session editor, local known-device names, diagnostics.
- `ControllerClient`: Shizuku permission/binding and serial asynchronous Binder calls.
- `HotspotBackend`: shell user-service; runtime API discovery, capability callback, allowlist changes, start/stop, data accounting, timer and conditional restoration.
- `SessionService`: opt-in foreground watcher; detects external starts and posts session/error notifications.
- `StopService`: short user-invoked notification command.
- `BootReceiver`: attempts to resume the opted-in watcher after boot.
- `SessionRules` / `SessionPolicy`: validated limits and addresses, tested without Android.

Restricted framework methods are accessed by reflection only in the exempt Shizuku user-service. Public API availability alone is not treated as permission to configure a hotspot. Android/OEM updates may change compatibility; failures are displayed explicitly.

## Validation status

Compiled against SDK 36. APK signing and package metadata verified. Platform-independent input, timer boundary, no-limit and MAC normalisation/deduplication checks pass. No physical Pixel or emulator was available in the build environment. Runtime Shizuku binding, shell permission checks, allowlist enforcement and the counter's freshness still require on-device verification. This is a development build, not a tested guaranteed-spend limiter.

On your Pixel, verify:

- Shizuku connects and diagnostics show a shell UID plus available allowlist capability.
- An externally started hotspot posts one session prompt; stopping/restarting posts a new prompt.
- Password mode lets devices connect; enrolment remembers their addresses.
- Selected-only mode rejects an unselected device and live edits change access.
- A two-minute timer turns the hotspot off, including with the screen locked.
- Data accounting follows a controlled download. Only then rely on data-threshold shutdown with a margin.
- Startup delay does not prematurely clear rules; user stop restores owned prior device settings.
- Loss/restart of Shizuku is clearly shown and stale sessions recover safely.

## Third-party software

Shizuku API/provider 13.1.5 and their aidl/shared components, by RikkaApps, are used under the MIT licence. See `SHIZUKU-LICENSE` and the licence bundled in the APK assets.

## 0.2.1 caller-identity fix

Fixes `SecurityException: Package android does not belong to 2000` when applying or starting a session. A fresh shell application context replaces the system-derived package context, which can inherit the system AppOps identity. Before constructing hotspot, power and usage managers, Keeper verifies package name, AppOps package, attribution UID/package, and Android package ownership. Diagnostics expose these identity fields without credentials. The authorise button now distinguishes an actual permission request from access that is already granted. The controller service version is bumped so an APK update replaces the old daemon.

This fix is compiled and regression-tested against an Android-context fixture reproducing inherited caller attribution. Real Pixel enforcement still needs on-device confirmation.

## App identity (0.2.2)

The approved stopwatch / Wi-Fi / padlock concept is retained at `docs/branding/hotspot-keeper-concept.png`. Native vector resources reproduce the mark for a sharp adaptive launcher icon, circular launcher masks and Android themed icons. Notifications use a matching alpha-only status-bar mark, with the coloured icon as their optional large icon. The SDK build compiles Android resources and generates `R.java`, matching the standard Gradle resource pipeline.
