# DeviceLink protocol

**Status: draft 0.1 — no interoperable implementation exists yet.**

The protocol is independent of Android and Linux implementation languages.
Its purpose is to let multiple peers discover, authenticate, negotiate, and
exchange events or data locally.

## Design requirements

- Symmetric multi-peer operation.
- Explicit protocol version negotiation.
- Authenticated peers; encryption for all application traffic.
- Capability advertisement and per-peer authorization.
- Separate control and bulk-data paths.
- Stable event IDs, ordering metadata, acknowledgements, and idempotency.
- Bounded message and transfer sizes; defensive parsing.
- No dependency on internet access or a central server.

## Message families (provisional)

- `hello`: protocol versions and peer identity proof.
- `pairing`: pairing challenge and confirmation.
- `capabilities`: offered/required capabilities and permission state.
- `command`: request with unique ID, expiry, and typed payload.
- `event`: event ID, origin peer, sequence, and typed payload.
- `ack`: acknowledgement or structured error.
- `transfer_offer`, `transfer_accept`, `transfer_chunk`, `transfer_finish`:
  negotiated bulk transfer lifecycle.

These names are conceptual. Field definitions, wire encoding, limits, and
cryptographic profile must be specified before implementation.

## Open decisions

1. Protobuf versus CBOR for control messages.
2. Exact mutual TLS identity and certificate rotation model.
3. QR payload and short-code authentication ceremony.
4. Event retention and reconciliation after long disconnections.
5. File chunk size, resume semantics, and transfer concurrency limits.

Do not treat this draft as a stable public protocol. Changes before 1.0 are expected.
