# Hotspot Keeper

Keeper manages an Android phone's normal internet-sharing hotspot with device allowlists, total-session time limits and session alerts. It uses the existing hotspot name/password and a Shizuku shell helper. **Current version: 0.3.1.** No root, analytics, cloud service or app Internet permission.

## Setup and everyday use

1. Install Keeper over the existing APK to retain preferences, using the same signing certificate. Prefer upgrading with the hotspot off: replacing the privileged helper can safely end an unfinished protected session.
2. Install [Shizuku](https://shizuku.rikka.app/download/). Enable Developer options and Wireless debugging in Android, pair and start Shizuku, then authorise Keeper in Settings → Shizuku connection. The in-app Setup & help guide explains each step. Pairing/start normally needs Wi-Fi; after reboot start Shizuku again and reconnect Keeper.
3. Choose device access and a total time limit in the Session rules sheet. **Save defaults** while off does not start the hotspot or require notification permission. **Start hotspot** reviews/applies rules before requesting startup; **Apply changes** edits a running session. Cancel leaves drafts unsaved.
4. Allow notifications for session alerts. Optionally enable **Detect hotspots started elsewhere** to automatically apply saved rules to Android-initiated sessions. Starting inside Keeper does not enable this option. External starts can briefly permit password access before the allowlist is applied.
5. Remember connected devices in Settings → Known devices. Tap a device to rename/remove it or inspect its reported name, IPs and Wi-Fi address. Removing a saved device does not change live session rules or silently enable unrestricted access.

The home screen separates applied session rules from next-session defaults, shows the timer and friendly connected-device names, and provides one start/stop action. Settings holds connection management; Setup & help and Troubleshooting contain explanations and diagnostics.

## Rules and limits

- Device access uses Android's supported MAC allowlist. If restricted access is unsupported or the selection is empty, Keeper reports the failure. MAC addresses may rotate on client devices and are not strong authentication.
- The linear time slider snaps to **0, 5, 10, 20, 30, 45, 60, 90, 120 minutes**. Limits use the observed session start; applying new rules never restarts the timer. Zero removes the Keeper limit while Android's normal idle shutdown still applies. An already elapsed duration requires confirmation. Late attachment is labelled tracked-since.
- The controller rejects stale live edits if the hotspot session changed. It restores owned access settings only while those settings still match Keeper's applied rules, preserving unrelated SSID/password changes.
- **There are no data counters or data caps.** Legacy allowance fields are ignored.

## Device names and addresses

Keeper reads best-effort DHCP client metadata through Android's tethering callback in the Shizuku helper. A reported hostname is optional. IPv4/IPv6 addresses are numeric display hints, not a guarantee that the client is reachable. Some Android versions/devices may deny or omit this information; diagnostics exposes discovery status and the app falls back gracefully.

Display priority:

1. Your saved friendly name.
2. Device-reported DHCP hostname.
3. Unknown device with a Wi-Fi address suffix.

Names previously auto-generated from MAC suffixes are treated as placeholders so an available reported hostname can replace the placeholder. Discovery never overwrites a manual label or automatically grants access. Remembering a device saves its MAC entry; advertised names and IPs are not persisted. Device details distinguish the editable local name from the reported name and addresses.

Wi-Fi association events remain authoritative for Keeper's connected list; DHCP lease metadata is filtered to associated clients and cleared between sessions. Names are bounded and sanitised for plain-text display. No NetBIOS probing, mDNS scans, reverse DNS or Internet lookup is performed. Discovery failure does not affect hotspot control, device rules or timers.

Android's [TetheredClient source](https://android.googlesource.com/platform/packages/modules/Connectivity/+/refs/heads/main/Tethering/common/TetheringLib/src/android/net/TetheredClient.java) describes the optional advertised hostname. Actual availability still needs Pixel verification; a successful fixture test is not proof that the phone exposes the API.

## Notifications and background operation

Externally detected sessions raise one prominent vibrating confirmation alert while saved rules apply immediately. Opening Keeper acknowledges it; later status updates are silent. Actual sound, heads-up and vibration depend on Android notification preferences and Do Not Disturb. Cutoff is announced once Android reports the hotspot off; manual/idle shutdown stays quiet. Tap notifications to open Keeper; there are no extra action buttons.

Optional external detection requires a minimal silent foreground watcher: a runtime hotspot broadcast triggers verified state checks, with a 30-second idle fallback and three-second active checks. The privileged helper also registers hotspot callbacks. These listeners need live processes; they cannot reliably launch a stopped ordinary app. A session-only monitor keeps status/cutoff notifications during Keeper-managed sessions even with external detection off, and exits after the session ends. After force-stop, reopen Keeper.

The shell helper enforces timers independently of the activity, using a partial wake lock while a timed session requires it. Shizuku access is broader than hotspot-only access; authorise only trusted apps. If the helper restarts with unfinished owned rules, it safely ends that protected session before restoring settings. Loss of control is reported explicitly.

## Architecture and contributor guidance

Read [AGENTS.md](AGENTS.md) for invariants, component boundaries, privacy, accessibility and verification requirements.

| Component | Responsibility |
| --- | --- |
| `MainActivity` | Native screens, navigation, draft editing and presentation |
| `KeeperUi`, `TimerSlider` | Shared styling/icons and accessible discrete timer input |
| `ControllerClient` | Shizuku authorisation, binding and serial Binder transport |
| `HotspotBackend` | Shell-only orchestration, actual state, allowlists, timer and restoration |
| `TetheredDeviceDiscovery` | Optional reflective tethering callback, metadata parsing and failure isolation |
| `DeviceIdentity` | Pure-Java display policy, normalisation and bounded name sanitisation |
| `DeviceIdentityJson` | Binder snapshot codec, independent of UI and privileged discovery |
| `SessionService` | Session notifications, optional detection and active-session monitoring |
| `SessionRules`, `SessionPolicy` | Pure validation and timer policy |

Restricted API reflection stays outside the UI. Binder commands validate caller UIDs, and shell context/package/AppOps attribution is verified before system access. Device metadata is separate from access policy.

## Build and validation

Requires **JDK 17, SDK platform 36 and Build Tools 36.0.0**:

```sh
scripts/build.sh /absolute/path/to/android-sdk
```

Output: `dist/HotspotKeeper-debug.apk`. The SDK-only build downloads checksum-pinned Shizuku dependencies, compiles resources/Java, signs/verifies the APK and runs policy, timer/device-rule, shell-identity, metadata-display and discovery-callback fixture tests. Gradle is also supported; no wrapper is bundled.

GitHub Actions builds APK artifacts. `DEBUG_KEYSTORE_BASE64` preserves CI signing identity when configured; otherwise CI generates a key that may not update existing installations. Never commit signing keys or downloaded/generated artifacts. App version/code must agree in Gradle, the SDK script and UI; helper changes also require a Shizuku service version bump.

Automated checks do not validate device-specific privileged APIs or rendered UI. [UX review](docs/ux-review.md) contains Pixel checks; additionally verify named/unnamed clients, a manual override, numeric IPs, disconnection, a new session, and graceful missing/denied discovery. No Android emulator/phone is available in the build environment. Approved generated designs are concepts, not screenshots of this build.

## Branding and dependencies

The stopwatch/Wi-Fi/padlock concept is in `docs/branding/hotspot-keeper-concept.png`; native launcher/themed/notification resources reproduce it. Shizuku API/provider 13.1.5 and aidl/shared components by RikkaApps are MIT licensed; see `SHIZUKU-LICENSE` and the licence bundled in APK assets.
