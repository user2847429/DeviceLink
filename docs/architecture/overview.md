# Architecture overview

**Status: design draft, not an implementation specification.**

## Model

DeviceLink is a network of symmetric peers. Any peer may offer and consume
capabilities. The design must support more than two peers without assuming a
permanent controller or host.

## Layers

1. **Identity and trust** — stable per-installation device identity, key storage,
   explicit pairing, peer revocation, and trust state.
2. **Discovery** — mDNS on supported local networks; manual address and QR
   fallback; gateway probing for hotspot topologies; optional BLE wake-up.
3. **Session/control plane** — persistent authenticated channel for hello,
   version negotiation, capability advertisements, commands, events, and
   acknowledgements.
4. **Data plane** — separately negotiated transfers for files and other bulk
   payloads. Use transfer IDs, bounded chunks, resumability, one-time tokens,
   and end-to-end integrity checks.
5. **Capability modules** — files, media, notifications, clipboard, apps, network,
   screen, camera, and input. A capability declares what it provides and what it
   requires; authorization is per peer and capability.
6. **Privilege broker** — Android-only adapter for Shizuku/root-backed operations.
   Modules request named, validated operations; they never receive a general
   arbitrary-shell interface.

## Initial technology choices

- Android application: Kotlin.
- Linux peer daemon: Rust, running as a user service; D-Bus integration via zbus
  can expose desktop facilities such as MPRIS and notifications.
- Protocol: language-neutral schema and normative documentation, with shared
  fixtures. Protobuf is the current candidate; final encoding is not frozen.
- Transport: TLS 1.3 with mutual peer authentication is the initial direction.
  Certificate/key pinning and QR/PIN pairing require a detailed threat model.
- TCP first; QUIC is deferred. gRPC is not required.
- Bluetooth is for discovery/wake-up and small control hints, not bulk transfer.

## Reliability and loop prevention

Important events need stable IDs, sequence numbers, acknowledgements, and
idempotent handling. Relayed events carry origin metadata to prevent notification
and clipboard echo loops. Reconnection must reconcile state rather than assume
that timestamps alone establish ordering.

## Platform constraints

Android API 35 is the minimum; target API 37. Capabilities must be negotiated
at runtime because APIs, permissions, ROM behavior, and foreground-service
restrictions vary by Android version. A missing capability must degrade cleanly,
not break the peer connection.

Linux is a first-class platform from the beginning. Windows is out of scope.

## Security invariants

- No data channel before successful pairing and peer authentication.
- No implicit trust based only on being on the same Wi-Fi network.
- No cloud, account, Firebase, or Google Play Services dependency.
- Explicit user approval for each sensitive capability.
- Least privilege; Shizuku operations are narrow and auditable.
- File transfer verifies integrity and never silently overwrites by default.
