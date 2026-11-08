# Device identity and pairing

**Status:** design draft. This document describes intended behavior; it is not
implemented and is not a security audit.

## Goals

- Give every installation a stable cryptographic identity.
- Pair nearby devices deliberately, without accounts, cloud services, or a
  central authority.
- Authenticate both peers and protect every session against interception.
- Keep identity independent from IP addresses, MAC addresses, hostnames, and
  discovery mechanisms.
- Support Android and Linux without requiring the same platform key store.

## Identity model

Each installation creates one long-term signing key pair on first launch.
The private key never leaves the device. The public key is the cryptographic
identity; a stable device ID is derived from a versioned hash of its canonical
public-key encoding.

The UI may show a friendly device name, model, and a short fingerprint for
human recognition. These are labels, not proof of identity. Changing a name,
network, IP address, or discovery route must not change the identity.

### Key storage

- **Android:** use Android Keystore through the platform cryptography APIs.
  Prefer a hardware-backed key when the device supports it, but do not require
  hardware backing. The app must detect and report the actual security level
  rather than assume it.
- **Linux:** use a platform secret-storage backend where available. A
  permission-restricted local key store is a documented fallback; the private
  key must not be written to logs, exported by normal UI flows, or stored in
  the repository.
- Both implementations expose the same narrow operations: create identity,
  return public identity, sign a protocol-defined challenge, and revoke or
  replace the local identity. Platform-specific key material never crosses
  the protocol boundary.

The Android implementation now has an initial identity primitive: an EC
P-256 (`secp256r1`) key pair generated in Android Keystore, with ECDSA/SHA-256
signatures. The stable peer ID is `dl1:` followed by the SHA-256 fingerprint
of the DER-encoded SubjectPublicKeyInfo. The private key is not exported.

This is only local identity storage and challenge signing. It does not yet
provide certificates, mutual TLS, QR exchange, pairing, or trusted-peer
storage. Those features must bind this public key to the reviewed TLS 1.3
session before any application data is accepted. Future platform clients must
implement the same public-key encoding, signature profile, and peer-ID rule.
DeviceLink must not implement cryptographic primitives itself.

## Pairing flow

Pairing is an explicit, time-limited ceremony:

1. The user chooses **Add device** on one peer. That peer creates a fresh
   pairing session with a cryptographically random nonce and a short expiry.
2. The app displays a QR code containing only the protocol version, the
   initiator's public identity, the session nonce, and the expiry. It contains
   no private key, password, or reusable bearer secret.
3. The other peer scans the QR code and shows the identity and device details
   it can verify. The user must explicitly approve.
4. The peers establish a candidate encrypted session and bind the pairing
   exchange to that session. Both sides derive the same short authentication
   string from the authenticated handshake transcript using a standard
   protocol exporter or equivalent reviewed mechanism.
5. Both devices display the same short code. The users compare it through
   the UI and confirm on both devices. A mismatch, timeout, cancellation, or
   changed transcript aborts pairing.
6. Only after both confirmations do the peers persist each other's public
   identity as trusted. A successful pairing grants no plugin permissions
   automatically.

The short code is a human comparison aid, not a substitute for cryptographic
authentication. It must be derived from the live session transcript, not from
a static QR value. Pairing must be resistant to replay and QR reuse.

## Trusted peer records

A trusted peer record contains the peer's stable identity, public key,
user-visible name, pairing time, and protocol metadata needed for migration.
It must not treat an IP address or MAC address as a trust credential.

On every later connection, the peer must prove possession of the private key
corresponding to the stored public identity. A different key for the same
device label is a new identity, not an automatic update.

## Changes, loss, and revocation

- A key change is a security-relevant event. Show a clear warning and require
  a fresh pairing ceremony; never silently accept a replacement key.
- If a device is reset or its key is lost, the user removes the old trusted
  record and pairs it again.
- Provide a way to revoke a trusted peer locally. Revocation takes effect
  immediately for new sessions and terminates active sessions where possible.
- Key rotation must be designed as an explicit, authenticated protocol
  operation before it is implemented. No implicit trust migration.

## Session protection

The transport is intended to use TLS 1.3 with certificate/public-key
authentication pinned to the paired peer identity. Normal public web PKI
hostname checks alone are not a suitable device-pairing trust model.
Certificate or key verification must be strict; there must be no
"accept all certificates" mode, silent fallback to plaintext, or downgrade
to an unauthenticated connection.

The application protocol must bind its negotiated version and peer identities
to the authenticated session. Important commands and pairing messages need
unique IDs, replay protection, and bounded lifetimes.

## Failure behavior

Pairing and connection attempts fail closed on invalid signatures, unknown
keys, expired or reused nonces, unsupported mandatory protocol versions,
transcript/code mismatch, malformed identity data, or unexpected key changes.
Errors shown to users should explain the next safe action without exposing
private material.

## Implementation gates

Before marking this design implemented, the project must:

- choose and pin reviewed Android and Rust cryptographic/TLS libraries;
- prove that Android and Linux generate and verify the same identity profile;
- add shared positive and negative pairing fixtures;
- test expiry, replay, mismatched codes, key changes, revocation, and
  interrupted pairing;
- review TLS peer verification and downgrade behavior;
- document backup, reinstall, reset, and key-loss behavior.

## References

- [Android Keystore system](https://developer.android.com/privacy-and-security/keystore)
- [RFC 9525 — Service Identity in TLS](https://www.rfc-editor.org/rfc/rfc9525.html)

These references inform platform key protection and TLS identity verification.
They do not by themselves define the DeviceLink pairing protocol.
