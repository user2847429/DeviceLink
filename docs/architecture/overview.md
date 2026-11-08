# Capability architecture

**Status: foundational design and metadata scaffold. Not a remote execution API.**

DeviceLink uses a small, composable capability model inspired by the Unix
preference for focused tools. The analogy is about modularity, not about
exposing a shell: a capability offers a small set of named operations with
validated inputs and explicit authorization requirements.

## Core boundaries

- **Core:** identity, pairing/trust, authenticated sessions, protocol
  negotiation, authorization, event delivery, and transfer coordination.
- **Capability modules:** focused features such as `device.apps`,
  `device.files`, `device.media`, `device.notifications`,
  `device.clipboard`, `device.input`, `device.display`, and
  `device.network`.
- **Android adapters:** Android framework APIs, permissions, intents,
  MediaSession, document providers, and optional Shizuku integration.
- **Privilege broker:** narrowly named and validated privileged operations.
  No capability receives a general shell or unrestricted system access.

## Capability contracts and authorization

A capability descriptor has a stable namespaced ID, its own version, a list of
operation IDs, and the grants it requires. Individual sensitive operations can
require explicit user approval.

The local registry stores metadata only. The typed request model and pure
authorization policy now provide an initial boundary for future dispatch:
requests are size-limited and checked against the authenticated session
context, trusted-peer status, local capability ID and version, exact operation
allowlist, required grants, and explicit approval for sensitive operations.

These checks do not execute anything, grant Android permissions, establish a
session, or prove that a caller supplied trustworthy session facts. The future
session layer must create the peer context only after cryptographic
authentication. Each operation must also validate its own payload schema
before an Android adapter is called.

## Composition

Capabilities should call shared core services through narrow interfaces rather
than opening their own sockets or implementing their own security. The core
routes authorized requests to the local module and returns structured results.
Bulk data uses the separate transfer plane, not large control messages.

Modules must be independently testable. Missing or unavailable modules must
not prevent peers from connecting. Events need IDs and idempotent handling to
avoid duplicate actions and relay loops.

## Platform strategy

Android is the only active application target. Keep capability IDs, operation
IDs, versioning, and protocol semantics platform-neutral so future independent
implementations can interoperate. Android-specific implementation remains
behind adapters. Do not build Linux, Windows, or macOS applications as part of
the current phase.

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

These names are planning examples, not promises that every operation is
available on every Android device. Each module will document platform limits,
required grants, and whether the operation is read-only or changes device state.
