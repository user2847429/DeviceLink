# DeviceLink threat model (draft 0.1)

**Status: initial security design.** This document records the threats and security
invariants that must guide implementation. It is not a security audit or proof
that the current scaffold is secure.

## Assets to protect

- Device identity keys and pairing state.
- Files and other user data transferred between peers.
- Commands that control media, apps, notifications, input, or system settings.
- Capability grants and privileged operations performed through Shizuku/root.
- Availability and integrity of active peer sessions.

## Trust boundaries

1. **Untrusted local network:** Wi-Fi, hotspots, multicast discovery, and IP
   addresses are attacker-controlled. Being on the same network is never proof
   of identity or consent.
2. **Unpaired peer:** discovered devices are unknown. Discovery must not grant
   access to data, commands, or capabilities.
3. **Paired peer:** pairing establishes a cryptographic identity relationship,
   but does not automatically authorize every capability.
4. **Capability boundary:** each sensitive capability has a separate grant,
   revocation path, and least-privilege implementation.
5. **Privilege broker:** Shizuku/root is a high-impact boundary. Modules may
   request only named, validated operations; arbitrary shell strings are forbidden.
6. **Operating system:** Android and Linux enforce their own permissions and
   sandbox boundaries. DeviceLink must not imply that pairing bypasses them.

## Main threats and required mitigations

| Threat | Required mitigation |
|---|---|
| Rogue device advertises itself during discovery | Treat discovery as a hint only; require authenticated pairing |
| Man-in-the-middle during pairing or connection | Use a reviewed authenticated key exchange / TLS 1.3 profile and compare a short authentication value out of band |
| Replay of an old command or event | Unique message IDs, session binding, expiry, sequence tracking, and idempotent processing |
| Malformed or oversized network messages | Strict framing, bounded lengths, parser validation, and fuzz tests |
| Compromised or misused paired peer | Per-capability approval, least privilege, visible peer identity, and immediate revocation |
| Unauthorized file overwrite or path traversal | User-selected destination, safe path handling, no overwrite by default, size limits, and content hash verification |
| Privilege escalation through Shizuku/root | Narrow allow-listed operations, argument validation, audit logging, and no general shell API |
| Network denial of service | Connection limits, rate limits, timeouts, bounded queues, and resource quotas |
| Stolen or replaced device | Revoke peer trust; rotate/re-enroll identity keys; never silently trust a changed key |
| Sensitive information exposed in logs | No file contents, secrets, pairing codes, or private keys in logs |

## Pairing and identity principles

- Generate a unique key pair per installation using platform-appropriate secure
  storage where available.
- Never transmit private keys.
- Bind the pairing transcript to both peer identities, protocol version, and
  negotiated cryptographic parameters.
- Require explicit confirmation on both devices. A short code is a comparison
  aid, not a secret sent over the network.
- Store a peer's public identity and trust state; detect unexpected key changes.
- Provide a clear way to revoke a paired peer.
- Do not use IP addresses, device names, or MAC addresses as identity.

The exact cryptographic handshake, key derivation, certificate format, QR
payload, and rotation rules remain open until reviewed against established
standards and implemented with maintained libraries. DeviceLink must not invent
its own cryptographic primitives.

## Security invariants (must hold in tests)

1. No application data or control command is accepted before peer authentication.
2. Discovery never implies pairing or authorization.
3. A paired peer receives only explicitly granted capabilities.
4. Revocation blocks new sessions and invalidates existing authorization.
5. Every privileged operation is allow-listed and validated at the broker.
6. All parsers enforce documented bounds before allocating or acting on input.
7. File transfer verifies integrity and does not silently overwrite by default.
8. Secrets and user data are excluded from diagnostic logs.

## Out of scope for the first release

- Protection against a fully compromised operating system or rooted device
  whose owner has granted unrestricted control to malicious software.
- Protection against physical access to an unlocked device.
- Anonymous internet relay or cloud-based rendezvous.
- Guaranteed availability on hostile networks.

These limitations must be stated clearly. Root/Shizuku support increases what
DeviceLink can do, but also increases the impact of a compromised plugin or peer.

## Before implementing the handshake

- Select a maintained, interoperable TLS 1.3 / authenticated key exchange
  approach; do not write custom cryptography.
- Define the QR and short-code ceremony, including MITM resistance.
- Define key storage and peer revocation behavior on Android and Linux.
- Add cross-language test vectors and negative tests.
- Review the design before enabling remote control or privileged operations.
