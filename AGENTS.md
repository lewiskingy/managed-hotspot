# Working on Hotspot Keeper

## Purpose and scope

Keeper manages the phone's existing internet-sharing hotspot: explicit device access rules, total-session duration and clear session notifications. Preserve the native navy/mint UX. Data metering/caps were intentionally removed; do not reintroduce them or hide the need for Shizuku. Keep optional external-start detection separate from starting inside Keeper. Do not add the Quick Settings tile until it is requested as implementation work.

## Component boundaries

- `MainActivity`: screen composition, navigation, draft editing and presentation. Do not put privileged reflection, network discovery or protocol parsing in UI callbacks. When adding a capability, encapsulate its policy/model and platform adapter rather than growing the activity with unrelated routines.
- `KeeperUi` / `TimerSlider`: shared styles, line icons and accessible input behaviour.
- `ControllerClient`: Shizuku authorisation, connection and serial Binder transport.
- `HotspotBackend`: shell-only orchestration, verified hotspot state, allowlist ownership/restoration and timer enforcement.
- `TetheredDeviceDiscovery`: optional shell-only tethering callback adapter; DHCP metadata discovery/parsing/failure handling belongs here. Use the backend worker executor for its mutable state.
- `DeviceIdentityJson`: Binder snapshot encoding/decoding, separate from presentation and discovery.
- `DeviceIdentity`: pure-Java untrusted metadata normalisation and display-name precedence. It must remain independent of Android UI and privileged APIs.
- `HotspotSettingsSheet`: isolated credential editor; do not route secret responses into routine UI snapshots or diagnostics.
- `HotspotProfileController` / `HotspotProfilePolicy`: narrow privileged name/password operations and pure input validation. Preserve security mode, bands and access rules via copy builders; keep fixed/redacted error messages.
- `SessionRules`: pure validation and timer arithmetic. Do not duplicate these rules in widgets.
- `SessionService`: session lifecycle notifications and optional external-start monitoring. Active-session notification monitoring is allowed without changing the external-detection preference.

## Behavioural invariants

1. Keep draft edits, saved next-session defaults and currently applied controller rules distinct. Saving defaults must not start tethering or silently enable external detection. Cancel must not persist edits.
2. Time limits run from the observed hotspot session start. Rule edits must not reset the baseline. Zero means no Keeper time limit; normal Android idle shutdown remains. Reject stale-session live edits and warn before applying an already elapsed limit.
3. Selected-only mode requires an explicit supported allowlist. Never silently widen access after an error or removal of a saved device. Restore only device settings still owned by Keeper; preserve unrelated SSID/password changes.
4. User-saved friendly names take precedence over advertised hostnames, then fall back to a MAC suffix. Treat all reported names/IPs as untrusted display hints, never authentication, automatic enrolment, allowlist matching or proof of presence. Association callbacks determine connected-device presence; DHCP leases can be stale.
5. Discovery is optional. Missing names, unsupported APIs and permission failures must degrade to existing MAC display, not disable hotspot control or timer enforcement. No NetBIOS/mDNS probes, reverse-DNS lookups or new network permissions without explicit feature scope.
6. Name/password changes must preserve unrelated configuration and distinguish next-start settings from the active network. Protect against stale edits. Never persist or log credentials, include them in routine snapshots, or silently change security mode. Pending credential changes must not make supposed live access-rule edits silently deferred by Android.
7. Session-start confirmation alerts once, review acknowledges it, routine updates stay silent. Confirm cutoff only after observed off. No notification action buttons unless requested. Do not repeatedly repost dismissed unchanged notifications. Never claim that Android guarantees sound/vibration/heads-up under user settings or DND.

## Privilege, data and compatibility

Restricted APIs run only in the shell helper. Preserve caller UID checks and validated shell package/AppOps attribution. Add narrowly scoped operations, never arbitrary shell execution. Use capability checks and explicit failure messages. Do not add root, Internet permission, telemetry or cloud dependencies unnecessarily.

Avoid persistent copies of observed IP addresses/hostnames unless required. Do not overwrite manual names with discovered values. Bound/sanitise device-controlled text and display it as plain text. Do not log hotspot credentials. Never commit keystores, signing secrets, downloaded dependencies, generated build outputs or APKs.

When helper code/protocol changes, bump the Shizuku user-service version in `ControllerClient` so existing daemons are replaced. Document that replacement can safely end an unfinished protected session. Keep app version/code aligned in Gradle, `scripts/build.sh`, visible UI and README.

## UX and accessibility

Use shared tokens and native controls, dp spacing, sp text, system insets, at least 48dp touch targets and meaningful accessibility labels. Keep session actions visible, editor actions anchored and long content scrollable. Use plain user-facing language; put setup explanations and technical details in Help/Troubleshooting. Preserve named devices and existing preferences across updates.

## Verification and delivery

Read README and relevant tests before changing behaviour. Run `scripts/build.sh /absolute/path/to/android-sdk` using JDK 17, SDK 36 and Build Tools 36.0.0. The build signs/verifies the APK and runs policy, rules, shell-identity and discovery tests. Add meaningful pure-model or adapter-fixture regression checks for changed logic; fixtures belong under tests and must never enter the APK.

Use `git diff --check`. Verify package version and signing compatibility for installable APKs. Run relevant CI and report the actual result. No claims of device testing, rendered screenshots, accessibility validation or working privileged API access without evidence. Update `docs/ux-review.md` and document remaining Pixel checks when no Android runtime is available.

Keep README current user/developer documentation, not a chronological chat log. PR descriptions should state final behaviour, validation and limits. Inspect current branch/PR state before publishing. A stacked PR must clearly identify its base and merge order. Do not merge a PR or publish a release unless authorised.
