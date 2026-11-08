# DeviceLink protocol

**Status: draft 0.3 — bounded binary control-frame codec implemented; transport, authentication, and encryption are not implemented.**

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

## Control frame format (draft 0.1)

All integer fields use network byte order (big-endian). A frame is exactly one control message; the transport must preserve frame boundaries or add an outer length prefix.

| Offset | Size | Field | Rule |
|---:|---:|---|---|
| 0 | 4 | Magic | ASCII `DLCP` |
| 4 | 1 | Frame format version | `1` |
| 5 | 4 | Protocol version | Currently `1` |
| 9 | 1 | Message kind | Stable numeric code from the table below |
| 10 | 2 | Message ID length | Unsigned, 0–128 bytes |
| 12 | 2 | Sender peer ID length | Unsigned, 0–128 bytes |
| 14 | 4 | Payload length | Signed integer, 0–65,536 |
| 18 | variable | Message ID | UTF-8, strict decoding |
| next | variable | Sender peer ID | UTF-8, strict decoding |
| next | variable | Payload | Opaque bytes |

The fixed header is 18 bytes. The maximum complete frame is 65,806 bytes. A decoder must reject oversized frames before allocating field buffers, reject invalid UTF-8, unknown kinds, unsupported format/protocol versions, inconsistent lengths, and trailing bytes.

### Stable message-kind codes

| Code | Kind |
|---:|---|
| 1 | `hello` |
| 2 | `pairing` |
| 3 | `capabilities` |
| 4 | `command` |
| 5 | `event` |
| 6 | `acknowledgement` |
| 7 | `transfer_offer` |
| 8 | `transfer_accept` |
| 9 | `transfer_chunk` |
| 10 | `transfer_finish` |

These numeric values are wire identifiers and must never be inferred from enum ordinals or reordered.

## Current implementation

The Android implementation has a platform-neutral in-memory `ControlMessage` model and `ControlMessageCodec`. The codec uses the format above, validates message limits, applies strict UTF-8 decoding, rejects unknown codes and malformed/trailing data, and caps frame size before parsing. Unit tests cover all message kinds, maximum-size frames, malformed input, and unsupported versions.

The payload remains opaque and message-family payload schemas are not yet defined. The codec is framing/serialization only: it does **not** authenticate peers, encrypt traffic, negotiate versions, or make a socket a trusted session. Do not expose it over an unauthenticated network transport.

## Message families (provisional)

- `hello`: supported protocol versions and peer identity proof.
- `pairing`: pairing challenge and confirmation.
- `capabilities`: offered/required capabilities and permission state.
- `command`: request with unique ID, expiry, and typed payload.
- `event`: event ID, origin peer, sequence, and typed payload.
- `acknowledgement`: acknowledgement or structured error.
- `transfer_offer`, `transfer_accept`, `transfer_chunk`, `transfer_finish`: negotiated bulk-transfer lifecycle.

Exact payload fields, canonical payload encoding, compatibility rules, and cryptographic profile must be specified before network session implementation.

## Implementation boundaries

- Android-specific APIs (Keystore, permissions, Shizuku, foreground services, Wi-Fi discovery) stay behind Android adapters.
- The protocol specification must not expose Android classes, Linux APIs, shell commands, or UI concepts.
- Future platform clients should be able to implement the documented messages without changing Android screens or Android-specific code.
- Bulk file data must not be embedded in control messages; the data plane will have its own bounded framing and resumable transfer rules.

## Security and transport

No custom cryptography. The codec is not a security boundary. The concrete maintained libraries, authenticated TLS 1.3 profile, peer identity binding, and QR confirmation ceremony must be selected and tested before implementation. Plaintext fallback is not permitted.

## Open decisions

1. Canonical encoding for typed message-family payloads.
2. Exact mutual TLS identity and certificate/key rotation model.
3. QR payload and short-code authentication ceremony.
4. Event retention and reconciliation after long disconnections.
5. File chunk size, resume semantics, and transfer concurrency limits.

Do not treat this draft as a stable public protocol. Changes before 1.0 are expected.
