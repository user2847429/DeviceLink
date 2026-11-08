# Secure session lifecycle

**Status:** pure state-machine policy and unit tests; no TLS socket integration yet.

The lifecycle separates TCP reachability from application trust:
- `TCP_CONNECTED`: a socket is reachable, nothing more.
- `TLS_HANDSHAKING`: TLS negotiation is in progress.
- `TLS_AUTHENTICATED`: a future adapter has completed TLS 1.3 mutual authentication and verified both certificate public keys against DeviceLink identities.
- `PAIRING_PENDING`: the encrypted, authenticated channel belongs to an identity not yet trusted. Capabilities remain unavailable.
- `READY`: an existing trusted identity matched, or both users confirmed first-time pairing.
- `CLOSED` / `FAILED`: terminal for this session; reconnect creates a new lifecycle.

`SecureSessionLifecycle` is a pure transition policy. It does not create sockets, perform TLS, inspect certificates, export keying material, persist trust, or authorize capabilities. In particular, `TLS_13_MUTUAL_AUTHENTICATED` is a contract for a future adapter, not evidence that the current app implements mutual TLS. The adapter may emit it only after both peer certificates and DeviceLink key identities have been verified.

No DeviceLink control messages or capabilities may be dispatched before `READY`. Pairing rejection and transport failure fail closed.

## Android integration gates

The planned adapter uses platform TLS sockets restricted to TLS 1.3, requires client authentication on the server, checks the remote leaf P-256 key against the expected DeviceLink identity, and verifies that the local Android Keystore key can sign TLS CertificateVerify. A first-time peer remains untrusted until both users compare the TLS-exporter-derived code and confirm. A trusted peer with a changed key is rejected.

Android documents `SSLSockets.exportKeyingMaterial` from API 31; DeviceLink requires API 35. Export must happen only after handshake completion and must fail closed if unavailable. Key-manager integration, mutual certificate requests, exporter agreement, and provider behavior still require instrumentation tests on supported Android builds and the two target devices.
