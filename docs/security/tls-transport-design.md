# TLS transport implementation notes

**Status:** initial Android TLS socket/session adapter implemented; instrumentation and physical-device verification are still outstanding. The adapter is not yet integrated into the peer service or pairing flow.

## Candidate Android TLS provider

The first Android implementation should use the platform JSSE provider through `SSLSocket`, restricted to TLS 1.3. Android exposes `android.net.ssl.SSLSockets.exportKeyingMaterial` from API 31 and provides `isSupportedSocket` to reject sockets that are not backed by the platform TLS provider. DeviceLink's minimum API level is 35, so the public API is available on supported Android versions.

The Android API reference describes this method in terms of RFC 5705. TLS 1.3 retains the exporter interface but defines a different HKDF-based construction in RFC 8446, section 7.5. Android's platform Conscrypt delegates to BoringSSL; BoringSSL implements the TLS 1.3 exporter when the exporter secret is available. This makes the platform API a strong candidate, but the exact API/provider behavior must still be verified in an Android instrumentation test on the supported OS versions. Never silently substitute a locally derived value if export fails or returns null.

The exporter call must happen only after the TLS 1.3 handshake has completed. Use one fixed, ASCII, application-specific label for pairing, for example `EXPORTER-DeviceLink-Pairing-v1`. Supply a deterministic, non-empty context encoding bound to the pairing ceremony (including the offer/session nonce and protocol version). Both endpoints must use byte-identical label and context. The exporter output is short-lived session material: do not persist it, log it, or reuse it across sessions.

References:
- Android `SSLSockets` API: https://developer.android.com/reference/android/net/ssl/SSLSockets
- TLS 1.3, RFC 8446 section 7.5: https://www.rfc-editor.org/rfc/rfc8446
- BoringSSL TLS 1.3 exporter implementation: https://chromium.googlesource.com/external/gob/boringssl/boringssl/+/HEAD/ssl/t1_enc.cc

## Binding TLS to DeviceLink identities

The existing installation identity is an EC P-256 key generated in Android Keystore for ECDSA/SHA-256. Android Keystore also associates an X.509 certificate with asymmetric key-pair entries. Because the current key is authorized for signing, its generated certificate is expected to be self-signed; this must be checked on real devices rather than assumed.

The initial adapter loads the AndroidKeyStore entry through a JSSE `KeyManagerFactory` for TLS client and server authentication. The session wrapper completes the handshake before returning, checks the peer certificate against the expected peer ID and pinned key, applies bounded connect/handshake timeouts, and removes the handshake read timeout after success. The socket factory derives the local certificate alias from the same `DeviceIdentityStore` instance to avoid identity/alias mismatch. Do not export the private key or create a second unrelated long-term identity. Verify that the certificate selected by the key manager contains the exact public key whose SPKI fingerprint produces the advertised `peerId`, and that the key can perform TLS 1.3 ECDSA CertificateVerify operations on supported devices.

Both endpoints must request and present client certificates (mutual TLS). A custom trust policy must not accept arbitrary certificates as trusted peers. During first-time pairing, it may accept only a structurally valid, self-signed P-256 identity certificate as an *untrusted pairing candidate*, then require the short authentication string derived from the actual TLS exporter and require confirmation on both devices. A valid TLS handshake alone must not create a trusted-peer record. For already paired peers, the presented certificate public key must match the stored trusted identity exactly; changed keys fail closed.

The first-time pairing comparison code is an out-of-band authentication step, not an encryption key or a substitute for TLS. The session must remain restricted until both users confirm. A mismatch, timeout, cancellation, failed signature, certificate/key mismatch, or missing client certificate terminates the connection.

## Required verification before implementation is considered complete

1. Android instrumentation test: create a TLS 1.3 client/server pair using AndroidKeyStore-backed keys on API 35+.
2. Assert both sides negotiate exactly TLS 1.3 and both present certificates whose public keys match their DeviceLink peer IDs.
3. Assert client-certificate authentication is mandatory; missing or invalid certificates fail the handshake.
4. Export pairing material on both sides after handshake completion with the same label and context; assert equal, non-null output of the requested length.
5. Assert different contexts and separate handshakes produce different exporter values.
6. Test the above on the Nothing Phone 1 and Xiaomi Pad 6, including the actual OS/provider builds used for DeviceLink development.
7. Add tests for unknown identities, trusted-key mismatch, expired pairing offers, replayed offers, and one-sided confirmation.

The current code does not yet have instrumentation evidence that AndroidKeyStore certificate selection, mutual TLS, and exporter behavior work across supported devices. Until these checks pass and the session is integrated with pairing/trust state, the app must describe TCP probing as reachability only and must not exchange DeviceLink application messages over a socket as if it were an authenticated session.
