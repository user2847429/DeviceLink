# TLS transport implementation notes

**Status:** Android TLS 1.3 socket/session adapter, identity-bound HELLO channel, trusted-peer server mode, and inbound foreground-service integration are implemented. Mutual TLS, exporter exchange, and trusted/untrusted inbound-session behavior pass API 35 emulator instrumentation. QR pairing, outbound UI connection, and physical-device verification remain outstanding.

## Android TLS provider

The first Android implementation uses the platform JSSE provider through `SSLSocket`, restricted to TLS 1.3. Android exposes `android.net.ssl.SSLSockets.exportKeyingMaterial` from API 31; DeviceLink's minimum API level is 35.

The exporter is available only after the TLS 1.3 handshake. Pairing uses the fixed ASCII label `EXPORTER-DeviceLink-Pairing-v1` and a deterministic, non-empty context bound to the pairing ceremony. Both endpoints must use byte-identical label and context. Exported material is short-lived: never persist or log it, and never substitute a locally derived value if export fails.

References:
- Android SSLSockets API: https://developer.android.com/reference/android/net/ssl/SSLSockets
- TLS 1.3, RFC 8446 section 7.5: https://www.rfc-editor.org/rfc/rfc8446

## Binding TLS to DeviceLink identities

The installation identity is an EC P-256 key generated in Android Keystore; the private key is non-exportable. The TLS key manager is pinned to the same alias and public key used by `DeviceIdentityStore`. Both endpoints request and present client certificates.

For outbound connections, the remote certificate must match the exact peer ID and public key supplied by the trusted-peer record. The inbound service uses a server-only TLS factory that checks the presented client certificate against the encrypted trusted-peer registry. Registry read errors, unknown keys, invalid certificates, and changed keys fail closed. The remote peer ID is derived from the verified certificate after mutual TLS.

A successful TLS handshake does not itself create trust. First-time pairing remains a separate QR-based ceremony using the actual TLS exporter-derived short authentication string and explicit confirmation on both devices.

## Authenticated control channel

After TLS authentication, `AuthenticatedControlChannel.establish` performs a symmetric HELLO exchange. Both peers send an empty HELLO before reading; the sender peer ID must match the identity authenticated by the TLS certificate. Malformed frames, wrong message kinds, unexpected payloads, mismatched IDs, EOF, or timeout close the session. Subsequent messages are checked against the authenticated remote peer ID.

The channel is a transport boundary, not an authorization layer. It does not grant Android permissions, pair unknown peers, or authorize capability operations.

## Foreground service integration

`PeerService` now hands accepted discovery-listener sockets to bounded inbound session workers. It loads the encrypted trusted-peer repository before enabling the secure listener, performs mutual TLS and HELLO, tracks one active channel per remote peer ID, and emits app-scoped lifecycle/message broadcasts. Work is bounded to four concurrent session workers and sixteen queued sockets. Stopping the service closes active channels and pending sockets.

This is inbound-only plumbing. The UI does not yet initiate outbound sessions, and the service does not dispatch received commands to capabilities. No pairing flow currently populates the trust registry in the UI, so an installation with no paired peers correctly rejects every inbound client.

## Verification and remaining work

- API 35 emulator: TLS 1.3 mutual authentication and exporter equality pass.
- API 35 emulator: trusted client completes HELLO and exchanges a control event with the service-level TLS primitives.
- API 35 emulator: client identity absent from the trusted registry is rejected during TLS.
- Still required: verify AndroidKeyStore certificate selection, mutual TLS, exporter behavior, and service lifecycle on Nothing Phone 1 and Xiaomi Pad 6.
- Implement QR first pairing, exporter-derived confirmation, two-sided confirmation, and durable trust.
- Implement outbound connection from discovered peer to a previously paired identity.
- Add typed message schemas and route commands through capability authorization before any operation executes.
- Test expired/replayed offers, one-sided confirmation, trust revocation, and changed identity keys.

Discovery and TCP reachability are not authenticated sessions. This document describes implementation progress, not a security audit.
