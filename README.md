# Hotspot Keeper

Android hotspot device access and total-session time limits, using a Shizuku shell helper. Version 0.3.0. No root, Internet permission, analytics or cloud service.

## Install and use

Install over your existing Keeper APK. Start Shizuku and authorise Keeper. Allow notifications and enable automatic rules. Apply your preferred rules once to save defaults; subsequent hotspot sessions reuse the last successful rules automatically. An external hotspot start can allow password access briefly before Keeper applies the device rules.

The linear 0–120 minute slider snaps to 0, 5, 10, 20, 30, 45, 60, 90 and 120. Zero removes the time limit while normal Android idle shutdown remains. Reapplying rules keeps the observed session start. Elapsed time and proposed end time are shown; an already elapsed limit warns that applying stops immediately. Late attachment is labelled tracked-since.

Remember connected devices and give them names. Selected-only mode uses Android's supported device allowlist. Private/randomised client addresses can change. Connected devices show saved names, with addresses as fallback. Owned access settings are restored when the session ends; unrelated changes are preserved.

Data counters and data caps are completely removed. Old saved allowance fields are ignored by the new controller. Update replaces the old Shizuku helper; a previously protected session may be ended safely during that replacement.

## Notifications

A new session raises one high-importance vibrating confirmation alert, pending until you open Keeper or apply rules. Saved rules enforce immediately without waiting for acknowledgement. Opening Keeper clears the confirmation alert; subsequent status is silent. The alert is posted once per observed session, including across watcher restarts. If automatic application fails, Keeper explicitly reports it.

Timer cutoff raises a vibrating notification once Android actually reports off. It has no action buttons: tap to clear it and open Keeper. All notifications open Keeper directly and have no extra action buttons. Manual and idle shutdown do not raise cutoff alerts.

A new Session confirmation and cutoff channel enables vibration and sound by default. Android notification preferences, Do Not Disturb and device policy control actual heads-up, sound and vibration delivery. Android may permit dismissal of ongoing notifications; Keeper does not repeatedly alert or vibrate to override a dismissal.

The minimal silent background-monitoring notification remains required for the foreground service. A runtime hotspot broadcast prompts immediate verified state checks, and the Shizuku controller already registers SoftApCallback. These listeners need live processes and cannot reliably launch a stopped ordinary app. Idle fallback checks are reduced to 30 seconds, active checks remain three seconds. Timer enforcement runs in the shell helper with a partial wake lock only while a timed session needs it. After reboot restart Shizuku; after force-stop reopen Keeper.

## Build

JDK 17, SDK platform 36 and Build Tools 36.0.0:

```sh
scripts/build.sh /absolute/path/to/android-sdk
```

Output: dist/HotspotKeeper-debug.apk. Gradle builds are also supported. The SDK build compiles resources and Java, signs the APK, and runs input, timer, device-rule and shell caller-identity regression checks. CI uses DEBUG_KEYSTORE_BASE64 when configured; otherwise its generated key may not update an existing installation. Local supplied APKs retain the existing development certificate. Never commit a keystore.

Restricted APIs run only in the Shizuku shell helper, which validates Binder caller UIDs and shell attribution. Device support and Android updates affect compatibility. If the helper restarts with unfinished owned rules it ends the old protected session safely before restoring settings. Runtime behaviour requires Pixel testing.

## Branding and dependencies

The stopwatch/Wi-Fi/padlock concept is retained in docs/branding/hotspot-keeper-concept.png. Native launcher, themed and notification resources reproduce it. Shizuku API/provider 13.1.5 and aidl/shared components by RikkaApps are used under MIT; see SHIZUKU-LICENSE and the APK asset.


## 0.3.0 interface

A native navy-and-mint dashboard replaces the single long form. Off, starting/stopping, active and unavailable states expose one main action. Applied device access and time limits are read from the controller snapshot, separately from saved defaults. Connected-device rows show friendly names and open device details.

Both rules rows open a bottom sheet with a scrollable body and anchored confirmation/cancel actions. Timer edits stay drafts, show an end-time preview and preserve the original session start. An already elapsed duration requires explicit stop confirmation. The controller rejects live edits if their expected session has changed. Save defaults works offline without starting the hotspot or requiring notification permission. Restricted defaults with no selected devices fail validation; removing a saved device never silently changes access to password mode.

Settings contains saved devices, optional external detection, live connection status and support links. Known devices have explicit rename/remove controls and connected-device enrolment. Setup & help explains Shizuku trust and purpose, Developer options, Wireless debugging, pairing/start, permissions and reboot recovery. Troubleshooting offers Android settings shortcuts and copyable diagnostics.

Starting or editing a managed session does not enable external detection. A session-only foreground monitor supplies status/cutoff notifications until that session ends, then exits when detection is off. Existing opted-in watchers remain enabled. Native typography uses sp, spacing uses dp, touch controls have 48dp minimum targets, system insets are respected, and scrolling handles large text or long device lists. Timer stops can be selected with touch, keyboard or accessibility scroll actions.

This branch is based on PR #3 while it remains open; merge #3 first. Controller protocol version is bumped to expose the applied allowlist and reject stale session edits. As with prior helper updates, an unfinished protected session can end safely during replacement. Prefer upgrading with the hotspot off.

Build and platform-independent rule tests pass. No Android runtime is available in the build environment, so actual screen captures, font scaling, permission-dialog flows, accessibility gestures, notifications and hotspot enforcement still need Pixel testing. The approved generated concepts are design references, not screenshots of this build.
