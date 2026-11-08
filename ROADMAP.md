# Roadmap

This roadmap is provisional. Items are goals, not promises or implemented features. Android is the active development target. Other operating systems are deferred until the Android implementation and protocol are mature.

## Phase 0 — repository foundation

- [x] Public repository and Apache-2.0 licensing.
- [x] Initial architecture and protocol design notes.
- [x] Android application build skeleton.
- [x] GitHub Actions Android debug build.
- [x] Confirm Android CI passes on the default branch.
- [x] Keep protocol boundaries platform-neutral for possible future independent implementations.

## Phase 1 — protocol and security design

- [x] Initial threat model and trust boundaries (draft; requires review as implementation evolves).
- [x] Initial device identity and key lifecycle design (draft; platform/library choices still need validation).
- [x] Initial pairing flow design (QR and session-bound short authentication code; implementation pending).
- [ ] Versioned control messages and capability negotiation.
- [ ] Language-neutral protocol fixtures, initially tested by the Android implementation.
- [ ] Select and validate maintained cryptographic libraries and a concrete secure-session profile.

## Phase 2 — Android peers

- [x] Pure Kotlin peer lifecycle state model and transition tests (state logic only; no Android service or networking yet).
- [x] Initial Android foreground peer service with explicit start/stop controls and persistent status notification (lifecycle only; no discovery or peer connection yet).
- [x] First-launch notification permission prompt and in-app permission status screen (notification and Android 17 local-network permission; local-network access is requested only when the user chooses it).
- [x] Initial settings screen and launcher-visible app list (launch-only; no broad package inventory permission).
- [ ] Android-to-Android discovery over mDNS plus manual address/QR fallback.
- [ ] Authenticated encrypted session between two Android devices.
- [ ] Reconnection and peer lifecycle tests.
- [ ] Multi-peer session handling.

## Phase 3 — first useful Android capabilities

- [ ] File send/receive with resumable chunks and SHA-256 verification.
- [ ] Android share-sheet target ("Send to device").
- [ ] Device list and per-peer capability permissions.
- [ ] Media-session control, prioritizing phone-to-tablet control.

## Later Android capabilities

Notification mirroring, clipboard, app transfer/update, hotspot and network controls, remote screen/display, camera, keyboard/touchpad, and automation API. Each requires its own design and platform-specific tests.

## Future platform ports

Linux, Windows, and macOS are not active targets in this phase. The protocol and capability model should remain documented and platform-neutral so that independent clients or ports can be developed later without adding platform-specific controls to the Android app.

## Android device experiments

Before committing to implementation details, validate the experiments recorded in the original project brief: DocumentsProvider behavior, media route volume, notification listener restrictions, multi-APK installation with Shizuku, hotspot control and BLE wake-up, virtual displays, remote camera restrictions, local network permission behavior, Shizuku startup, player queue APIs, and connection persistence.
