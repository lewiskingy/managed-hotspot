# Keeper 0.3.0 review guide

## Screens

- Home: header/settings, actionable connection status, one session card, applied/saved rules, connected devices only when on.
- Session rules: draft selection, linear snapping total-time slider, elapsed/end preview, anchored Apply/Save/Start and Cancel.
- Settings: saved devices, optional external detection, connection and support.
- Known devices: remember connected clients, add address, rename/remove details.
- Setup & help: numbered expandable initial setup, explanation of elevated access, reboot reminder and everyday help.
- Connection/troubleshooting: reconnect, Shizuku launch/download, Android notification/hotspot settings and copyable diagnostics.

## Validation

Automated: signed SDK build; timer/no-limit/expired boundaries; MAC validation/deduplication; forward/back slider stops and endpoint clamping; shell caller-attribution regression fixture.

Device review required (not completed in this environment):

1. Install with hotspot off. Confirm existing devices/defaults and detection choice are retained.
2. Dashboard off: save defaults without Shizuku; Cancel leaves preferences unchanged. Starting requests notification permission but never enables external detection implicitly.
3. Grant/deny notifications and Shizuku permissions. Denial leaves defaults usable and control failures actionable.
4. Start with password access and with selected devices. Confirm dashboard shows applied, not draft, rules and friendly connected-device names.
5. Edit a live rule, then cancel. Confirm the timer does not change. Apply a longer total duration; confirm the baseline remains fixed.
6. Choose an already elapsed duration. Verify confirmation, then timer shutdown. End/restart a session while an old sheet is open; stale Apply must fail.
7. Disable detection while managed session runs. Verify timer and cutoff alert remain; after off the idle monitoring notification disappears. Enable detection to rearm external hotspot handling.
8. Rename/add/remove devices, including removal of the last selected device. No action should silently widen live access.
9. Review navigation and rule sheet at large font size, narrow width, landscape, long device names and TalkBack. Confirm buttons remain reachable and slider steps through the documented stops.
10. Follow Setup & help on Pixel; verify Developer options, Shizuku and Android settings links, reboot reconnection and notification guidance.

No runtime screenshots are included: the build environment has no emulator or connected phone. Do not substitute the design concepts for evidence of rendered UI.

## Device discovery (0.3.1)

- Check a client supplying a DHCP hostname and one supplying none. Manual friendly names must always win. Old MAC-generated placeholder names may yield to reported names.
- Device details must distinguish editable local name, reported name, numeric IPs and MAC; missing metadata must say unavailable/not provided.
- Remembering a device must not copy network-controlled names into permanent manual labels. Clearing a local name should expose the live reported name again.
- Verify updates after DHCP changes/disconnection and a new session. Old leases must not create connected clients. Discovery unavailable/denied must not block timers or allowlists.
- Automated model and reflective callback fixtures cover precedence, sanitisation, IP deduplication, Wi-Fi filtering, association filtering, partial field failures, session clearing and registration denial. Real Pixel API access remains unverified.
