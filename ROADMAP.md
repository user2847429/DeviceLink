# Roadmap

This roadmap is provisional. Items are goals, not promises or implemented features.

## Phase 0 — repository foundation

- [x] Public repository and Apache-2.0 licensing.
- [x] Architecture and protocol design notes.
- [x] Android and Linux build skeletons.
- [x] GitHub Actions build workflow.
- [x] Confirm CI passes on the default branch (Android debug APK and Linux checks).

## Phase 1 — protocol and threat model

- [x] Initial threat model and trust boundaries (draft; requires review as implementation evolves).
- [x] Initial device identity and key lifecycle design (draft; platform/library choices still need validation).
- [x] Initial pairing flow design (QR and session-bound short authentication code; implementation pending).
- [ ] Versioned control messages and capability negotiation.
- [ ] Interoperability fixtures shared by Kotlin and Rust.

## Phase 2 — minimal connected peers

- [ ] Android peer can start/stop and expose status.
- [ ] Linux peer can start/stop and expose status.
- [ ] Discovery over mDNS plus manual address/QR fallback.
- [ ] Authenticated encrypted session.
- [ ] Reconnection and peer lifecycle tests.

## Phase 3 — first useful capabilities

- [ ] File send/receive with resumable chunks and SHA-256 verification.
- [ ] Android share-sheet target ("Send to device").
- [ ] Device list and per-peer capability permissions.
- [ ] Linux desktop integration.

## Later capabilities

Media session control, notification mirroring, clipboard, app transfer/update,
hotspot and network controls, remote screen/display, camera, keyboard/touchpad,
and automation API. Each requires its own design and platform-specific tests.

## Device experiments

Before committing to implementation details, validate the experiments recorded
in the original project brief: DocumentsProvider behavior, media route volume,
notification listener restrictions, multi-APK installation with Shizuku, hotspot
control and BLE wake-up, virtual displays, remote camera restrictions, local
network permission behavior, Shizuku startup, player queue APIs, connection
persistence, and Linux D-Bus/FUSE behavior.
