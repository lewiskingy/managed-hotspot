# Play submission preparation

Keeper is free, ad-free and MIT-licensed. Publishing is a separate manual step: this PR neither submits an app nor guarantees Play acceptance. Android 12+ and a separately installed, started and authorised Shizuku are requirements. Explain these prominently in the listing and review instructions.

## Before a production submission

1. Create/verify the developer account. Google's registration charge is US$25 once; taxes/conversion may differ. Complete current identity/device verification and, if applicable to a new personal account, required closed testing (currently at least 12 opted-in testers for 14 continuous days). Recheck Console requirements at submission.
2. Establish a permanent production upload key outside the repository. Back it up securely. Enrol in Play App Signing, retaining package `uk.me.theflat.hotspot`. A production certificate differs from existing debug installs: Android normally requires uninstalling them first. Export Keeper names/rules first; hotspot passwords and permissions are not backed up. Stop the hotspot before migrating, then re-authorise Shizuku and import/review defaults. Do not promise seamless upgrades across certificates.
3. Publish `docs/privacy/index.html` to a stable public HTTPS page (for example GitHub Pages) and verify it works without login. It mirrors the policy bundled in `app/src/main/assets/privacy.txt`; update both together. Enter the real URL in Play Console. Configure a suitable public support contact; the current in-app contact is GitHub Issues.
4. Complete Data safety based on the actual release and SDK audit. Keeper has no Internet permission or developer collection backend, but document local device identifiers, Shizuku processing and user-selected backup providers accurately. External browser/payment and document-provider flows need separate consideration. No ads declaration, content rating, target audience, app access instructions and screenshots are required. Do not label this app a VPN or claim reliable data caps.
5. Declare/review the `specialUse` foreground service with a demonstration video: user enables optional external-start detection or starts a timed session; notifications explain it; stopping/disabling returns idle. Explain the Shizuku shell helper, restricted hotspot APIs, user consent, debugging prerequisite and exact feature scope for review under Device and Network Abuse rules. Review current non-SDK API restrictions and device compatibility. Approval is uncertain; no policy evasion or hidden privilege acquisition.
6. Test on a real Pixel: full fresh-install walkthrough, disabled/denied permissions, Wi-Fi/pairing failures, reboot recovery, running-session edits, allowlists, timeout, notification acknowledgement and backup migration. Check large text/TalkBack, rotation, system insets, dark mode and links. Fixture tests are not device validation.
7. Provide original icon, phone screenshots, feature graphic, concise listing and a reviewer setup video/instructions. Explain that setup needs a trusted Wi-Fi network but an already-running Shizuku normally does not require permanent Wi-Fi.

## Build and signing

Use JDK 17, Android SDK 36/Build Tools 36.0.0 and Gradle 8.11.1 (AGP 8.10.1). CI builds an **unsigned review AAB** and runs lint; that artifact cannot be submitted as-is. For production, set `RELEASE_STORE_FILE`, `RELEASE_STORE_PASSWORD`, `RELEASE_KEY_ALIAS`, `RELEASE_KEY_PASSWORD` in a secure local/CI environment and run `scripts/release.sh`. Inspect the signing certificate and AAB with official bundletool before uploading. Never commit keys/passwords or use the debug certificate for production. Increment versionCode for every Play upload. Production uploading and release publishing remain manual.

`scripts/build.sh /path/to/sdk` remains the debug APK and regression-test workflow. Its development key is intentionally separate. The new Gradle bundle workflow validates the packaging path, not Shizuku functionality.

## Optional support

`app/src/main/res/values/publishing.xml` has an empty `support_url`; no support row is shown until configured. A future link must be a real HTTPS URL. Keeper must stay completely usable without payment: no unlocks, badges, rewards, priority services or paid features. Call this optional support/tips, not a charitable donation. Before enabling it in a Play build, review Google's current Payments policy and FAQ and the payment platform's fees/terms. The direct contribution exception has specific conditions (100% to the creator and no digital benefits); do not assume every Buy Me a Coffee checkout qualifies. If uncertain, leave the link out of the Play build. Covering the registration fee is the intent, not a promise of how much is needed or that payments stop automatically.

Official references (recheck at submission):
- https://support.google.com/googleplay/android-developer/answer/6112435
- https://support.google.com/googleplay/android-developer/answer/14151465
- https://support.google.com/googleplay/android-developer/answer/10144311
- https://support.google.com/googleplay/android-developer/answer/10281818
- https://support.google.com/googleplay/android-developer/answer/13392821
- https://shizuku.rikka.app/guide/setup/
