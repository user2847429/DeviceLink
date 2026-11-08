# DeviceLink

**Local, private, peer-to-peer connectivity for Android and Linux.**

DeviceLink is an open-source project to connect multiple devices directly over a local network. Every peer can provide and consume capabilities: there is no permanent client/server role, account, cloud relay, or dependency on Google Play Services.

> **Project status: early scaffold.** The repository currently contains the initial architecture, protocol direction, Android/Linux build skeleton, and CI. The connection protocol and product features are not implemented yet.

## Goals

- Android ↔ Android and Android ↔ Linux, with multi-peer groups.
- Local-first operation over Wi-Fi or a phone hotspot; internet access is not required.
- Encrypted peer connections and explicit per-device, per-capability permissions.
- Small core with documented, extensible feature modules.
- Free/open-source software, with no Google Play Services, Firebase, or cloud account requirement.

## Platforms

| Component | Initial implementation |
|---|---|
| Android app | Kotlin; minimum Android 15 (API 35), target Android 17 (API 37) |
| Linux peer | Rust; user-session daemon, with desktop integration through D-Bus |
| Shared contract | Versioned, language-neutral protocol specification and interoperability tests |

Windows is out of scope.

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

The initial CI builds the Android debug APK and checks/tests the Linux Rust workspace. Local prerequisites:

- JDK 17
- Android SDK Platform 37 and Build Tools 36.0.0
- Gradle 9.6.0 (or use the version configured by CI)
- Rust stable toolchain

Android:

```sh
gradle :android:app:assembleDebug
```

Linux:

```sh
cargo check --workspace
cargo test --workspace
```

## Development status

See [ROADMAP.md](ROADMAP.md) and [CONTRIBUTING.md](CONTRIBUTING.md). GitHub Actions results are shown in the repository's Actions tab.

## License

Apache License 2.0. See [LICENSE](LICENSE) and [NOTICE](NOTICE).
