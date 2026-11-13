# Capability architecture

**Status: foundational capability contracts and authorization policy implemented; inbound authenticated transport integrated. Remote capability dispatch is not implemented.**

DeviceLink uses a small, composable capability model inspired by the Unix preference for focused tools. The analogy is about modularity, not exposing a shell: a capability offers a small set of named operations with validated inputs and explicit authorization requirements.

## Core boundaries

- **Core:** identity, pairing/trust, authenticated sessions, protocol negotiation, authorization, event delivery, and transfer coordination.
- **Capability modules:** focused features such as `device.apps`, `device.files`, `device.media`, `device.notifications`, `device.clipboard`, `device.input`, `device.display`, and `device.network`.
- **Android adapters:** Android framework APIs, permissions, intents, MediaSession, document providers, and optional Shizuku integration.
- **Privilege broker:** narrowly named and validated privileged operations. No capability receives a general shell or unrestricted system access.

## Capability contracts and authorization

A capability descriptor has a stable namespaced ID, its own version, a list of operation IDs, and the grants it requires. Individual sensitive operations can require explicit user approval.

The local registry stores metadata only. The typed request model and pure authorization policy check request bounds, authenticated and trusted session context, local capability ID and version, exact operation allowlist, required grants, and explicit approval for sensitive operations.

These checks do not execute anything, grant Android permissions, establish a session, or prove that a caller supplied trustworthy session facts. The future session layer must create peer context only after cryptographic authentication. Each operation must also validate its own payload schema before an Android adapter is called.

## Authenticated transport boundary

The Android peer service accepts inbound TCP sockets, performs TLS 1.3 mutual authentication against the encrypted trusted-peer registry, then completes an identity-bound HELLO exchange. Unknown certificates and trust-store read failures fail closed. Session workers and queued sockets are bounded; stopping the service closes active and pending connections.

The current service integration receives authenticated control messages and emits app-scoped events, but does not route commands to capabilities. QR-based first pairing and outbound session initiation from the UI remain to be implemented. A discovered peer is not implicitly trusted, and TCP reachability is not an authenticated session.

## Composition

Capabilities should call shared core services through narrow interfaces rather than opening their own sockets or implementing their own security. The core will route authorized requests to local modules and return structured results. Bulk data uses a separate transfer plane, not large control messages.

Modules must be independently testable. Missing or unavailable modules must not prevent peers from connecting. Events need IDs and idempotent handling to avoid duplicate actions and relay loops.

## Platform strategy

Android is the only active application target. Keep capability IDs, operation IDs, versioning, and protocol semantics platform-neutral so future independent implementations can interoperate. Android-specific implementation remains behind adapters. Do not build Linux, Windows, or macOS applications as part of the current phase.

## Planned initial modules

| Module | Example operations |
|---|---|
| `device.peers` | discover, list, forget |
| `device.apps` | list, launch, inspect |
| `device.files` | browse, offer, receive, resume |
| `device.media` | get-state, play, pause, seek, set-volume |
| `device.notifications` | list-supported, dismiss, relay |
| `device.clipboard` | read-with-consent, write, subscribe |
| `device.input` | key, pointer, touch — only where Android permits |
| `device.display` | get-info, request-capture — with explicit consent |
| `device.network` | get-status, hotspot operations where permitted |
| `device.system` | get-limited-info, request-approved-action |

These names are planning examples, not promises that every operation is available on every Android device. Each module will document platform limits, required grants, and whether the operation is read-only or changes device state.

## Transport independence and product experience

The logical peer identity, trusted-peer registry, authenticated session, protocol framing, and capability authorization are independent of the underlying link. The Android transport roadmap includes infrastructure Wi-Fi/hotspot, Wi-Fi Direct, Bluetooth Classic RFCOMM, and BLE/GATT for constrained control/discovery. A route change must never weaken identity verification or authorization. Simultaneous infrastructure Wi-Fi and Wi-Fi Direct is hardware/firmware-dependent and must be detected at runtime; the app must not promise concurrency or silently disconnect a user's existing network.

The user experience should use an adaptive Material 3 design, with pinned/reorderable favorite actions, per-device layouts, and explicit connection/pairing states. Italian, Russian, and English are launch localization requirements; further languages must be supported through Android resource localization rather than hard-coded UI strings. See [product and connectivity requirements](../product/experience-and-connectivity.md).
