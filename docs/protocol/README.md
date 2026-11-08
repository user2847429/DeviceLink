# DeviceLink protocol

**Status: draft 0.2 — design and in-memory models only. No network protocol is implemented.**

The protocol is language- and platform-neutral. Android is the first implementation target; future clients on other operating systems may implement this contract independently. The Android app must not depend on desktop-specific behavior.

## Design requirements

- Symmetric peer operation; no permanent client/server role.
- Explicit protocol version negotiation.
- Authenticated peers and encryption for all application traffic.
- Capability advertisement and per-peer authorization.
- Separate control and bulk-data paths.
- Stable event IDs, ordering metadata, acknowledgements, and idempotency.
- Bounded message and transfer sizes; defensive parsing.
- No dependency on internet access, accounts, or a central server.

## Current control-message model

The first in-memory model has these fields:

| Field | Meaning | Current rule |
|---|---|---|
| `version` | Protocol version | Must equal the supported version (currently 1) |
| `messageId` | Unique message identifier | Required; UTF-8 encoded size at most 128 bytes |
| `senderPeerId` | Stable sender identity | Required; UTF-8 encoded size at most 128 bytes |
| `kind` | Message category | One of the message kinds below |
| `payload` | Opaque message-specific bytes | At most 65,536 bytes |

These limits apply to the current model, not yet to a serialized network frame. The Kotlin model validates these constraints and has Android local unit tests. The eventual wire decoder must enforce the same limits before allocating large buffers.

## Message families (provisional)

- `hello`: supported protocol versions and peer identity proof.
- `pairing`: pairing challenge and confirmation.
- `capabilities`: offered/required capabilities and permission state.
- `command`: request with unique ID, expiry, and typed payload.
- `event`: event ID, origin peer, sequence, and typed payload.
- `acknowledgement`: acknowledgement or structured error.
- `transfer_offer`, `transfer_accept`, `transfer_chunk`, `transfer_finish`:
  negotiated bulk-transfer lifecycle.

Message names and the in-memory Kotlin enum are provisional. Exact fields for each family, canonical encoding, framing, compatibility rules, and cryptographic profile must be specified before a network implementation begins.

## Implementation boundaries

- Android-specific APIs (Keystore, permissions, Shizuku, foreground services, Wi-Fi discovery) stay behind Android adapters.
- The protocol specification must not expose Android classes, Linux APIs, shell commands, or UI concepts.
- Future platform clients should be able to implement the documented messages without changing Android screens or Android-specific code.
- Bulk file data must not be embedded in control messages; the data plane will have its own bounded framing and resumable transfer rules.

## Security and transport

No custom cryptography. The concrete maintained libraries, authenticated TLS 1.3 profile, peer identity binding, and QR confirmation ceremony must be selected and tested before implementation. Plaintext fallback is not permitted.

## Open decisions

1. Canonical wire encoding and framing (Protobuf versus CBOR remains open).
2. Exact mutual TLS identity and certificate/key rotation model.
3. QR payload and short-code authentication ceremony.
4. Event retention and reconciliation after long disconnections.
5. File chunk size, resume semantics, and transfer concurrency limits.

Do not treat this draft as a stable public protocol. Changes before 1.0 are expected.
