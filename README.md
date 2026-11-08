# DeviceLink

**Local, private, peer-to-peer connectivity for Android devices.**

DeviceLink is an open-source project for connecting multiple devices directly over a local network. Every peer can provide and consume capabilities: there is no permanent client/server role, account, cloud relay, or dependency on Google Play Services.

> **Project status: early scaffold.** Android is the only active application target for the initial development phase. The repository contains architecture notes, early protocol models, an Android build skeleton, and CI. Device-to-device networking, secure pairing, and product features are not implemented yet.

## Goals

- Android ↔ Android connectivity, including multi-peer groups.
- Local-first operation over Wi-Fi or a phone hotspot; internet access is not required.
- Encrypted peer connections and explicit per-device, per-capability permissions.
- A small, extensible core with documented feature modules.
- Free/open-source software, with no Google Play Services, Firebase, or cloud account requirement.

## Platform strategy

| Area | Current scope |
|---|---|
| Android app | **Active development target** — Kotlin; minimum Android 15 (API 35), target Android 17 (API 37) |
| Protocol and data formats | Platform-neutral specifications, designed to allow future independent implementations |
| Linux, Windows, macOS | Possible future community ports; no application, interface, or active implementation planned now |

The initial project will focus on Android-to-Android use. Future operating systems should be able to implement the documented protocol without requiring changes to the Android app or its user interface. Platform-specific code will remain behind clear boundaries.

## Architecture at a glance

- **Identity and pairing:** stable device identity; QR/PIN-assisted pairing; mutually authenticated encrypted connections.
- **Discovery:** mDNS, direct address/QR, hotspot-friendly gateway probing, and optional Bluetooth LE wake-up.
- **Control plane:** persistent channel for commands, events, capability negotiation, and acknowledgements.
- **Data plane:** separate, short-lived transfers for files and other bulk data, with one-time transfer tokens and integrity checks.
- **Capabilities:** peers advertise what they can provide and consume; permissions are granted per peer and feature.
- **Privilege boundary:** optional Shizuku integration on Android, isolated behind named operations. Features must not receive arbitrary shell access.

See [the architecture notes](docs/architecture/overview.md) and [protocol draft](docs/protocol/README.md).

## Planned feature areas

File access and dropzone; media-session control and metadata; notifications; clipboard; app/package transfer; network and hotspot controls; remote screen/display; remote camera; keyboard and touchpad input; public intents/broadcasts for automation apps.

These are roadmap items, **not implemented features**. Some depend on Android version, app-specific APIs, foreground-service rules, or elevated privileges. Their limitations will be documented rather than hidden.

## Build

The active CI target is the Android debug APK. Local prerequisites:

- JDK 17
- Android SDK Platform 37.0 and Build Tools 37.0.0
- Gradle 9.8.2 (or the Gradle version supplied by your distribution)

Android:

```sh
cd android
gradle --no-daemon :app:assembleDebug
```

## Development status

See [ROADMAP.md](ROADMAP.md) and [CONTRIBUTING.md](CONTRIBUTING.md). GitHub Actions results are shown in the repository's Actions tab.

## License

Apache License 2.0. See [LICENSE](LICENSE) and [NOTICE](NOTICE).
