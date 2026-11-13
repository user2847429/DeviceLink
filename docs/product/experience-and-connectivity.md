# DeviceLink product experience and connectivity

**Product requirements draft — Android first.** These are product and architecture requirements, not claims that the features are already implemented.

## Product principles

1. Security is mandatory on every transport and every network. A local Wi-Fi, hotspot, Wi-Fi Direct, or Bluetooth link is not automatically trusted.
2. Connectivity is opportunistic: use the best mutually supported route for the task, but never weaken authentication or authorization when changing route.
3. Keep the common actions immediate and discoverable. Advanced controls should be customizable rather than crowding the main screen.
4. Offline and internet-independent operation is a first-class use case. DeviceLink must not require an account, cloud service, Google Play Services, or internet access for local peer control.
5. Android is the only active application target. Keep protocol contracts platform-neutral for future implementations.

## Transport architecture

All transports terminate at the same authenticated session abstraction. Discovery, link establishment, authentication, authorization, and capability execution are separate stages.

### Wi-Fi infrastructure and hotspot

- Keep the existing IP transport for peers already reachable on the same LAN or phone hotspot.
- Discovery may use mDNS and UDP broadcast where the current network permits them.
- Internet access is not required; an isolated LAN or local-only hotspot is sufficient.
- Do not assume that internet reachability implies peer reachability, or vice versa.

### Wi-Fi Direct (Wi-Fi P2P)

- Add an Android Wi-Fi P2P transport for direct device-to-device connections without an access point or internet.
- Use the Android `WifiP2pManager` framework APIs and report unsupported hardware, permission denial, group negotiation failure, and disconnects clearly.
- Treat simultaneous infrastructure Wi-Fi plus Wi-Fi Direct as a device capability, not a guarantee. Wi-Fi chip/firmware combinations differ; query platform support and handle the OS/network callbacks. Do not disconnect a user's existing Wi-Fi network without explicit consent.
- When concurrent operation is unavailable, offer a clear choice: keep the current Wi-Fi connection or temporarily switch to Wi-Fi Direct. Restore/leave network state cleanly when the P2P group ends.
- The transport supplies a byte stream or socket path to the shared session layer; it does not create a separate trust model.

### Bluetooth

Use two complementary Android transports according to the task:

- **Bluetooth Classic RFCOMM:** preferred Bluetooth option for a reliable bidirectional stream for small control commands and responses, where both devices and Android permissions support it.
- **Bluetooth Low Energy (BLE/GATT):** useful for discovery, presence, wake-up/coordination, and very small control messages such as media volume or playback state. Keep GATT payloads bounded and account for negotiated MTU, fragmentation, connection interval, and background execution constraints. Do not use BLE as the default bulk-file transport.
- Larger files should use Wi-Fi infrastructure or Wi-Fi Direct. If Bluetooth file transfer is ever offered, set conservative size limits and make the slower speed explicit.
- Bluetooth does not imply internet access. Users must be able to choose a Bluetooth-only connection without joining Wi-Fi or mobile data.

The media use case is remote control of playback on the other device (for example, changing the volume of a tablet acting as a speaker); it does not require DeviceLink to relay the audio stream.

### Route selection and coexistence

- Maintain one peer identity and one logical session regardless of the active transport.
- A peer may expose multiple candidate routes at once: LAN, hotspot, Wi-Fi Direct, Bluetooth Classic, and BLE.
- Prefer an available high-throughput route for file transfer; prefer a low-power route for tiny commands when it improves battery use or avoids changing the network.
- Do not automatically migrate an active session between paths until the session-resumption/rekey design is specified and tested. Initial implementation may reconnect securely on a new path.
- Never duplicate non-idempotent commands across multiple routes. Message IDs, acknowledgements, deduplication, expiry, and idempotency rules apply across transports.
- Show the selected route and its limitations in the device/session details. Allow a user to prefer or disable individual transports.
- Network changes, route loss, and Bluetooth/Wi-Fi permission revocation must close or suspend the affected transport safely without silently falling back to plaintext.

## Security requirements for every transport

- Use the same installation identity from Android Keystore and the same trusted-peer registry regardless of bearer.
- Run TLS 1.3 mutual authentication over stream transports (TCP over LAN/P2P and RFCOMM) where supported by the Android socket abstraction. For GATT, use an authenticated, encrypted application protocol with strict message framing and the same peer identity/pairing guarantees; do not assume BLE link encryption alone establishes DeviceLink trust.
- Bind every authenticated session to the peer public key, peer ID, protocol version, fresh nonces, and the selected transport context where appropriate.
- First pairing must use a short-lived QR invitation and an out-of-band comparison code derived from actual session key material, followed by explicit confirmation on both devices.
- No automatic trust based on proximity, shared Wi-Fi, Bluetooth bonding, MAC address, SSID, IP address, or successful discovery.
- Re-check capability grants and operation-specific user consent after authentication. A trusted device is not automatically allowed to perform every operation.
- Bound packet sizes, queues, retries, concurrent sessions, and resource use. Validate every length before allocation. Rate-limit unauthenticated handshakes and discovery responses.
- No arbitrary shell, no plaintext fallback, no custom cryptography, and no silent security downgrade.
- Threat-model replay, relay, downgrade, denial-of-service, stale QR invitations, key replacement, revocation, and compromised paired devices. Pairing and transport changes require negative tests, not only happy-path tests.

## Device capability inventory

- Provide a device information screen for the local Android device and a capability summary for each authenticated remote peer.
- Report hardware/framework capabilities separately from current runtime state. "Wi-Fi Direct supported" is not the same as "Wi-Fi Direct currently available"; "Bluetooth present" is not the same as "Bluetooth enabled".
- Local detection must use Android framework feature declarations and documented APIs. Unknown or permission-blocked values must be labelled unknown, never silently treated as unsupported.
- Include Wi-Fi feature/band information, Wi-Fi Direct, Bluetooth Classic, BLE, and other useful hardware features such as NFC and USB host where Android exposes them. Exact 802.11 generation, Bluetooth version, profiles, codecs, and simultaneous radio operation should only be shown when a reliable platform API exposes them.
- Remote capability data is informational until received over an authenticated, trusted DeviceLink session. Validate its schema, version, sizes, and freshness. Do not accept capability claims from discovery packets, unauthenticated HELLO messages, or an IP/Bluetooth address.
- The initial binary snapshot schema is versioned and bounded (maximum 64 entries and 16 KiB); each feature has an explicit supported, unsupported, or unknown state. The Android service sends the local snapshot only after mutual TLS trust and identity-bound HELLO, and validates a received snapshot before forwarding it to the UI.
- Compare local and remote capabilities to explain which transports and actions are mutually available. A capability match never bypasses pairing, authorization, permission checks, or explicit consent.

## User interface and visual design

- Adopt a polished Material 3 visual language, aiming for Material 3 Expressive where the Android UI toolkit and dependency versions support it.
- Use adaptive layouts for phone and tablet, with responsive navigation, comfortable touch targets, clear hierarchy, motion used sparingly, and accessibility contrast and text scaling.
- The home screen should emphasize the user's pinned/favorite actions and currently connected devices. Discovery and advanced configuration remain easy to reach but should not dominate the screen.
- Let users pin, reorder, hide, and group supported actions on the home screen. Store preferences locally and provide a reset-to-default option.
- Offer per-device favorite actions and sensible presets, for example a media-control layout for a tablet used as a speaker.
- Distinguish clearly between discovered, reachable, pairing pending, trusted, connecting, connected, and disconnected states. Never use a generic green "connected" state for mere discovery or TCP reachability.
- Make transport choice understandable: Automatic, Wi-Fi, Wi-Fi Direct, Bluetooth Classic, and BLE where supported. Show why a mode is unavailable and what changing it will do.
- Permission requests should be contextual: ask when the user starts the feature that needs the permission, explain why, and provide a route to system settings when denied. Do not repeatedly prompt after denial.
- Accessibility includes TalkBack labels, keyboard/switch navigation where practical, scalable typography, non-color status cues, and reduced-motion respect.

## Languages and localization

- Ship complete English, Italian, and Russian translations from the first public-quality UI release; English is the fallback language.
- Later language additions should stay focused on European languages, initially Spanish, French, German, and Portuguese (including Brazilian Portuguese) when translation quality is available.
- RTL languages (including Arabic and Hebrew), and non-European language packs such as Chinese, Japanese, and Hindi, are explicitly out of scope for the initial product phase. Do not add RTL-specific layout complexity now; keep the UI structured so this can be reconsidered deliberately later.
- Keep all user-facing strings in Android resources; no UI text hard-coded in Kotlin.
- Support Android per-app language preferences and an in-app language selector if appropriate. Changing language must not change protocol identifiers, stored peer IDs, or data formats.
- Handle plural rules, date/time/number formatting, long translated labels, and font coverage. Russian requires correct plural forms. Test layouts with long European translations and larger font settings.
- Use stable string resource names and translator comments with context. Keep protocol logs and diagnostic identifiers separate from localized UI labels.

## Delivery order

1. Keep the existing secure IP transport stable and complete QR pairing/trust.
2. Add transport abstraction and capability reporting without changing the security/session contracts.
3. Add Wi-Fi Direct with coexistence detection and fallback UX.
4. Add Bluetooth Classic RFCOMM for small bidirectional control.
5. Add BLE discovery and a deliberately small GATT control profile.
6. Add Material 3 adaptive UI, favorites/customization, and complete IT/RU/EN localization.
7. Expand languages and test on the Nothing Phone 1 and Xiaomi Pad 6 custom ROMs.

The Android framework and hardware determine whether multiple Wi-Fi interfaces can operate concurrently. The app must detect and explain limitations rather than promise simultaneous Wi-Fi STA and P2P on every device.
