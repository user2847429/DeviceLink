# DeviceLink protocol

**Status: draft 0.4 — bounded binary control codec and length-prefixed stream framing implemented; authenticated transport is not implemented.**

The protocol is language- and platform-neutral. Android is the first implementation target; future clients on other operating systems may implement this contract independently.

## Control frame

All integer fields use network byte order (big-endian). A codec frame contains an 18-byte fixed header followed by UTF-8 message ID, UTF-8 sender peer ID, and opaque payload. The magic is DLCP, format version is 1, protocol version is currently 1, and message-kind codes are stable values 1–10. Message IDs and peer IDs are limited to 128 bytes; payloads are limited to 65,536 bytes; maximum frame size is 65,806 bytes. Decoders reject invalid UTF-8, unknown kinds, unsupported versions, inconsistent lengths, and trailing bytes.

## Byte-stream framing

TCP does not preserve application message boundaries. ControlFrameStream prefixes each codec frame with a four-byte unsigned big-endian length. The accepted length range is 18–65,806 bytes and must be checked before allocating the body. EOF before any prefix byte is a clean end of stream; EOF inside the prefix or body is a truncated frame and must fail the session. Multiple frames may be concatenated. This helper provides framing only, not authentication or encryption.

## Message kinds

1. hello; 2. pairing; 3. capabilities; 4. command; 5. event; 6. acknowledgement; 7. transfer_offer; 8. transfer_accept; 9. transfer_chunk; 10. transfer_finish. Numeric codes are wire identifiers and must never be inferred from enum ordinals or reordered.

## Security boundary

The codec and stream framing are serialization utilities only. They do not authenticate peers, encrypt traffic, negotiate versions, or make a socket trusted. Do not expose them over an unauthenticated network transport. TLS 1.3 identity binding, QR confirmation, and typed payload schemas remain prerequisites before application messages are exchanged. No plaintext fallback or custom cryptography is permitted.

## Implementation boundaries

Android-specific APIs (Keystore, permissions, Shizuku, foreground services, discovery) stay behind Android adapters. Future platform clients must be able to implement the protocol without Android dependencies. Bulk file data belongs to a separate bounded and resumable data plane, not control messages. This draft is not yet a stable public protocol.
