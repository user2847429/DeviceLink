# TLS transport implementation notes

**Status:** Android TLS 1.3 socket/session adapter and identity-bound control HELLO channel implemented. Mutual TLS and exporter exchange pass the API 35 emulator instrumentation test. Physical-device verification and integration into the foreground peer service and pairing flow are still outstanding.

## Candidate Android TLS provider

The first Android implementation uses the platform JSSE provider through `SSLSocket`, restricted to TLS 1.3. Android exposes `android.net.ssl.SSLSockets.exportKeyingMaterial` from API 31 and provides `isSupportedSocket` to reject sockets that are not backed by the platform TLS provider. DeviceLink's minimum API level is 35, so the public API is available on supported Android versions.

The exporter call happens only after the TLS 1.3 handshake has completed. Pairing uses the fixed ASCII label `EXPORTER-DeviceLink-Pairing-v1` and a deterministic, non-empty context bound to the pairing ceremony. Both endpoints must use byte-identical label and context. The exporter output is short-lived session material: do not persist it, log it, or reuse it across sessions. Never silently substitute a locally derived value if export fails or returns null.

References:
- Android `SSLSockets` API: https://developer.android.com/reference/android/net/ssl/SSLSockets
- TLS 1.3, RFC 8446 section 7.5: https://www.rfc-editor.org/rfc/rfc8446
- BoringSSL TLS 1.3 exporter implementation: https://chromium.googlesource.com/external/gob/boringssl/boringssl/+/HEAD/ssl/t1_enc.cc

## Binding TLS to DeviceLink identities

The installation identity is an EC P-256 key generated in Android Keystore. The private key is non-exportable. The Android TLS adapter loads the matching AndroidKeyStore entry through a JSSE `KeyManagerFactory` for client and server authentication. Its key manager is pinned to the same alias and public key used by `DeviceIdentityStore`. The session wrapper completes the handshake before returning, checks the peer certificate against the expected peer ID and pinned key, applies bounded connect/handshake timeouts, and removes the handshake read timeout after success.

Both endpoints request and present client certificates (mutual TLS). For already paired peers, the presented certificate public key must match the stored trusted identity exactly; changed keys fail closed. First-time pairing remains a separate ceremony: an unknown peer must be compared out of band using the actual TLS exporter-derived short authentication string and confirmed on both devices. A valid TLS handshake alone must not create a trusted-peer record.

## Authenticated control channel

`AuthenticatedControlChannel.establish` runs only after `AndroidTlsSession` has completed pinned mutual TLS. It performs a symmetric HELLO exchange: both peers send first, then validate that the received message is an empty HELLO whose sender peer ID equals the identity authenticated by TLS. A malformed frame, wrong message kind, unexpected payload, mismatched peer ID, EOF, or timeout fails closed and closes the session. Subsequent control messages are also checked against the authenticated remote peer ID.

The channel is a transport boundary, not an authorization layer. It does not persist trust, pair unknown peers, grant Android permissions, or authorize capability operations. It is not yet connected to `PeerService` or the UI.

## Verification status and remaining work

- API 35 emulator instrumentation test: TLS 1.3 mutual authentication and exporter equality pass.
- Add negative instrumentation cases for missing/invalid client certificates and changed identity pins.
- Verify the same AndroidKeyStore certificate selection, mutual TLS, and exporter behavior on the Nothing Phone 1 and Xiaomi Pad 6.
- Integrate the channel with the peer listener and trusted-peer repository.
- Complete QR-based first pairing, exporter-derived confirmation, two-sided confirmation, and durable trust.
- Test unknown identities, trusted-key mismatch, expired/replayed pairing offers, and one-sided confirmation.

Until service and pairing integration plus physical-device verification are complete, discovery and TCP reachability must not be described as an authenticated DeviceLink session.
