# ADR 0006: Android device identity and GVP1 profile decryption

- Status: Accepted
- Date: 2026-08-27
- Scope: Android production client and Control API interoperability

## Decision

Each Android installation owns two independent, versioned key pairs:

1. A P-256 signing key generated and retained by Android Keystore. The private key is non-exportable.
2. An X25519 encryption key. Its 32-byte private value is generated locally and persisted only after AES-256-GCM wrapping with a non-exportable Android Keystore key.

The public registration document carries the installation UUID, key version, ES256 SPKI public key, raw X25519 public key, algorithms, and a hardware-backed capability signal. It never carries a private key or an imported VPN configuration.

Key loss, corruption, partial persistence, invalidation, or mismatched public/private material rotates both key purposes together and increments the version. A server envelope addressed to another version is rejected. No software fallback, static application key, or downgraded cipher is permitted.

## Device proof contract

The client signs these ASCII lines exactly, without a trailing newline:

```text
GANJ-DEVICE-PROOF-V1
<METHOD>
<PATH_AND_QUERY>
<BASE64URL_SHA256_OF_UNSIGNED_BODY>
<TIMESTAMP_EPOCH_SECONDS>
<BASE64URL_NONCE>
<KEY_VERSION>
```

The unsigned request body is the JSON body before adding `device_proof`; this avoids a circular body hash. The transported value is:

```text
gdp1.<timestamp>.<nonce>.<body-sha256>.<key-version>.<base64url-p1363-signature>
```

The server must reconstruct the unsigned body, compare the body digest in constant time, require an allowed clock window, consume the proof nonce once per device/key version, and verify the 64-byte IEEE-P1363 ES256 signature. It must never accept a proof under a different authenticated device or key version.

## GVP1 profile contract

The Control API creates the device-bound associated data as canonical JSON with lexicographically ordered keys:

```json
{"deviceId":"…","expiresAt":"…","profileId":"…","serverId":"…","serviceId":"…","userId":"…"}
```

GVP1 processing is:

1. X25519 between the server ephemeral key and the registered device encryption key.
2. `salt = SHA-256(associated_data)`.
3. HKDF-SHA256 with `info = "ganj-vpn-profile-v1"`, output 32 bytes.
4. AES-256-GCM with the 12-byte server nonce and the canonical associated data.
5. Envelope bytes: ASCII `GVP1`, 32-byte ephemeral public key, ciphertext, 16-byte tag.

All private, shared-secret, derived-key, decrypted-profile, and temporary ciphertext buffers owned by the client are overwritten after use. Authentication failure is terminal for that one-time lease.

## Compatibility and failure behavior

The Android application remains installable from API 24. Keystore AES-GCM and P-256 are required at runtime. X25519 uses the pinned Bouncy Castle lightweight implementation so Android 8–12 do not require a platform XDH provider. Dependency evidence is included in the Android SBOM and vulnerability gates.

If identity initialization or decryption is unavailable, the application reports a configuration/security failure and does not start the VPN tunnel. Session registration and authentication are a separate vertical slice and remain fail-closed until the Control API stores these public keys and issues a device-bound session.

## Rejected alternatives

- One key for signing and encryption: rejected for key-separation and rotation safety.
- Raw X25519 private key in preferences: rejected because application storage compromise would expose it directly.
- Manual URI, file, QR, or clipboard profile import: rejected by product policy.
- Static application-wide encryption key: rejected because it would break device binding.
- Zero-salt HKDF: rejected because the existing Control API binds derivation to the authenticated profile metadata digest.
