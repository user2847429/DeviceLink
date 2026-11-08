# Contributing

Thanks for your interest in DeviceLink. The project is at an early design and
scaffolding stage; please open an issue before starting substantial work.

## Principles

- Keep Android ↔ Android and Android ↔ Linux as first-class use cases.
- Preserve peer symmetry and multi-peer operation.
- Keep the core small; implement optional capabilities as isolated modules.
- Do not introduce accounts, cloud relays, Firebase, or Google Play Services.
- Encrypt peer traffic and require explicit pairing and per-capability consent.
- Treat Shizuku and other privileged operations as a narrow, auditable boundary.
- Document Android-version restrictions and unverified assumptions.

## Development

- Android: Kotlin, minimum API 35, target API 37.
- Linux: Rust stable, user-session daemon.
- Protocol: language-neutral, versioned, documented independently of either client.
- Run the relevant build and tests before submitting a pull request.

## Pull requests

1. Explain the problem and intended behavior.
2. Link related issues and identify affected platforms.
3. Include tests for protocol and security-sensitive changes.
4. Update documentation when behavior or protocol changes.
5. Never include credentials, private keys, personal data, or device logs containing
   sensitive information.

By submitting a contribution, you agree that it is provided under the repository's
Apache-2.0 license unless explicitly agreed otherwise.
