# Device identity and pairing

**Status:** implementation in progress. Local Android identity storage, pairing-offer validation, confirmation-code derivation primitives, two-sided confirmation, and a trusted-peer registry policy exist. Encrypted persistence, QR UI, authenticated transport, and end-to-end pairing are not implemented. This is not a security audit.

## Goals

- Give every installation a stable cryptographic identity.
- Pair nearby devices deliberately, without accounts, cloud services, or a central authority.
- Authenticate both peers and protect every session against interception.
- Keep identity independent from IP addresses, MAC addresses, hostnames, and discovery mechanisms.

## Identity model

Each installation creates one long-term signing key pair on first launch. The private key never leaves the device. The public key is the cryptographic identity; a stable device ID is derived from a versioned hash of its canonical public-key encoding.

The Android implementation has an EC P-256 (`secp256r1`) key pair generated in Android Keystore, with ECDSA/SHA-256 signatures. The stable peer ID is `dl1:` followed by the SHA-256 fingerprint of the DER-encoded SubjectPublicKeyInfo. The private key is not exported.

## Pairing offer contract

The current platform-neutral `PairingOffer` model defines the public QR invitation fields:

- protocol version (currently 1);
- peer ID;
- DER-encoded public key (bounded to 64–1,024 bytes);
- fresh 32-byte random nonce;
- absolute expiry timestamp in epoch milliseconds.

Validation recomputes the peer ID from the supplied public key, checks the protocol version and nonce size, and requires the expiry to be in the future and no more than five minutes away. The nonce is public replay-binding data, not a password or bearer credential. The offer model is not yet serialized into QR content and does not establish trust.

## Confirmation code contract

`PairingConfirmationCode` derives a six-digit comparison code using HMAC-SHA-256. Its key input must be at least 32 bytes of secret material exported by the authenticated TLS session. The domain-separated context includes the two peer IDs in sorted order and the session nonce, so both peers calculate the same value and the code is bound to that pairing context.

This helper does not create a TLS exporter, authenticate the session, or make the code independently secure. It must only be called after the future transport layer has authenticated both peer identities and supplied exporter material. Never derive the code from QR contents, public nonces alone, or a locally invented secret.

## Two-sided confirmation

The pure `PairingConfirmationState` requires both local and remote confirmation before reaching `Complete`, and rejects confirmation at or after expiry. Repeated confirmation is idempotent. This state model does not persist trusted peers or grant capabilities.

## Intended complete ceremony

1. The initiator creates a fresh nonce and a short-lived offer.
2. The other device scans the QR invitation and verifies the peer ID against the public key.
3. The devices establish an authenticated encrypted session, binding both identities and the offer nonce to the handshake.
4. Both derive the comparison code from the authenticated session's exporter secret and compare it out of band.
5. Both users confirm. Only then may each side persist the other's public identity as trusted.
6. Pairing grants no plugin permissions automatically.

A timeout, cancellation, invalid signature, replay, transcript mismatch, or one-sided confirmation must fail closed.

## Trusted peer records and revocation

A trusted peer record contains the peer's stable identity, public key, user-visible name, pairing time, and protocol metadata. It must not treat an IP address or MAC address as a trust credential.

A different key for the same device label is a new identity, not an automatic update. Provide local revocation; key rotation must be an explicit authenticated operation.

## Session protection

The intended transport is TLS 1.3 with strict peer authentication bound to the paired identity. Certificate/public-key verification must be reviewed and tested; normal web-PKI hostname checks alone are not a device trust model. No accept-all mode, plaintext fallback, or unauthenticated downgrade is permitted.

## Remaining implementation gates

- Select and pin maintained TLS/cryptographic libraries and implement a TLS exporter.
- Define and implement canonical QR serialization, scanning, and display.
- Implement encrypted trusted-peer storage, removal, and revocation.
- Bind TLS authentication to the paired public-key identity.
- Add shared interoperability fixtures and replay, expiry, key-change, and interrupted-pairing tests.
- Review backup, reinstall, reset, and key-loss behavior.

The platform-neutral TrustedPeerRegistry permits a trust record only after both confirmations, before expiry, and only when the peer ID matches the supplied public key. It rejects identity substitution and makes revocation explicit. The repository is an interface only: no production persistence backend is wired yet. A production Android repository must encrypt records at rest, make writes atomic, and terminate active sessions on revocation. These primitives are not a complete pairing feature and must not be used to trust a remote peer over the network yet.
