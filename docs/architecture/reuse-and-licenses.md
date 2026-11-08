# Reuse and licensing notes

This document records architectural ideas reviewed for DeviceLink. It is not a notice that code has been copied or that any dependency has been adopted.

## Dioxamine

Repository: https://github.com/rhythmcache/Dioxamine

The repository identifies itself as Apache-2.0. Its README describes a modular Android toolkit with ADB, scrcpy, package management, and a user-gated plugin bridge. Useful ideas for DeviceLink include:

- A dedicated, searchable installed-app/package view rather than mixing app management into device discovery.
- A curated set of named actions instead of exposing unrestricted shell access to every feature.
- Explicit, user-visible permission declarations for optional extensions.
- Separating transport/device connection from tools that operate over that connection.

Dioxamine also depends on separate projects (including ADB-related libraries and scrcpy). Their licenses and notices must be reviewed independently before considering any code or binary reuse. The top-level Apache-2.0 license does not automatically relicense dependencies.

## KDE Connect

Android repository: https://github.com/KDE/kdeconnect-android

The Android project is distributed under GPL-2.0 and GPL-3.0 terms (see its repository and component-level license files). DeviceLink is Apache-2.0. Therefore, do not copy or adapt KDE Connect implementation code into DeviceLink without a specific compatibility and distribution review. Its public feature concepts and protocol behavior can be studied as design references; implement independent code and document any protocol interoperability decisions.

Useful concepts to study:

- Pairing and trust management separated from discovery.
- Small, well-defined plugins/capabilities.
- Explicit per-device feature availability.
- Media control, file transfer, clipboard, and notification features as independent modules.
- Encrypted communication over the local network.

## Reuse policy for DeviceLink

1. Prefer Android platform APIs and small, maintained libraries with licenses compatible with Apache-2.0.
2. Do not copy code merely because the upstream project is open source.
3. Review the exact source file, dependency tree, license, copyright notices, and transitive native components before adding a dependency.
4. Keep optional integrations (such as Shizuku or ADB-related tooling) behind narrow interfaces and named operations.
5. Never expose arbitrary shell execution to ordinary feature modules or remote peers.
6. Record adopted dependencies and preserve their required notices in `NOTICE` and/or a dedicated third-party notices file.

## Current decision

No Dioxamine or KDE Connect code has been imported. DeviceLink remains an independent Android-first implementation. The immediate implementation priority is permission onboarding and the permission dashboard; device discovery and peer networking remain separate future work.
