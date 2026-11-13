# DeviceLink protocol

**Status: draft 0.6 — bounded binary control codec, length-prefixed framing, identity-bound HELLO, TLS 1.3 mutual authentication, and trusted-peer inbound transport are implemented and tested on an API 35 emulator. The protocol is not yet a stable public contract.**

The protocol is language- and platform-neutral. Android is the first implementation target; future clients on other operating systems may implement this contract independently.

## Control frame

All integer fields use network byte order (big-endian). A codec frame contains an 18-byte fixed header followed by UTF-8 message ID, UTF-8 sender peer ID, and opaque payload. The magic is DLCP, format version is 1, protocol version is currently 1, and message-kind codes are stable values 1–10. Message IDs and peer IDs are limited to 128 bytes; payloads are limited to 65,536 bytes; maximum frame size is 65,806 bytes. Decoders reject invalid UTF-8, unknown kinds, unsupported versions, inconsistent lengths, and trailing bytes.

## Byte-stream framing

TCP does not preserve application message boundaries. Control frames are prefixed with a four-byte unsigned big-endian length. Accepted lengths are 18–65,806 bytes and are checked before allocating the body. EOF before any prefix byte is a clean end of stream; EOF inside the prefix or body is a truncated frame. Multiple frames may be concatenated. Framing provides neither authentication nor encryption.

## TLS and HELLO

The Android transport uses TLS 1.3 mutual authentication. Outbound sockets pin the expected remote peer public key. The inbound server accepts a client certificate only if its exact P-256 public key is currently present in the encrypted trusted-peer registry. Unknown peers and registry read failures are rejected; there is no plaintext fallback.

After TLS, both endpoints send an empty HELLO before reading. The received senderPeerId must exactly match the peer ID derived from the authenticated certificate. A different identity, non-HELLO first message, non-empty HELLO payload, malformed frame, or timeout closes the channel.

The foreground peer service owns the inbound listener and hands accepted sockets to bounded session workers. It tracks authenticated channels and emits app-scoped events. The current integration is inbound-only; outbound connection UI and first-time pairing are not implemented yet. A clean installation with no trusted-peer records rejects all inbound clients by design.

## Message kinds

1. hello; 2. pairing; 3. capabilities; 4. command; 5. event; 6. acknowledgement; 7. transfer_offer; 8. transfer_accept; 9. transfer_chunk; 10. transfer_finish. Numeric codes are wire identifiers and must never be inferred from enum ordinals or reordered.

## Security boundary

The codec and framing are serialization utilities only. HELLO is safe only after mutual TLS has authenticated and pinned the remote identity. The TLS exporter is reserved for the first-pairing confirmation ceremony; exporter bytes must not be persisted or logged. TLS authentication does not authorize commands, grant Android permissions, or establish first-time trust.

QR pairing, typed payload schemas, and capability authorization before dispatch remain prerequisites before remote actions are implemented. No plaintext fallback or custom cryptography is permitted. Bulk file data belongs to a separate bounded and resumable data plane, not control messages.
