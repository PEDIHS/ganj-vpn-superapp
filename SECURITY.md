# Security Policy

Ganj VPN handles authentication, VPN entitlements, encrypted connection profiles and payment state. Security and privacy reports are treated as product incidents, not ordinary support requests.

## Supported Versions

| Channel | Supported | Security updates |
|---|---:|---|
| Production | Yes | Critical and high findings |
| Staging / beta | Yes | Findings that can reach production |
| Development snapshots | Best effort | Fix forward on `main` |
| Superseded production release | No | Update to a supported version |

Until the first production release, `main` is the only supported security baseline.

## Private Reporting

Do not open a public Issue containing an exploit, connection URI, UUID/password, bot token, access/refresh token, provider credential, signing material, receipt, user record, diagnostic archive or raw log.

Use a [private GitHub security advisory](https://github.com/PEDIHS/ganj-vpn-superapp/security/advisories/new). Include only synthetic test data and provide:

- affected version, component and environment;
- impact and realistic attack prerequisites;
- minimal reproduction steps or proof of concept;
- whether exploitation or public disclosure is suspected;
- a safe contact path for coordinated follow-up.

Do not test against other users, production payment methods or infrastructure you do not own. Do not retain, change or exfiltrate user data.

## Response Targets

These are response objectives rather than a promise of bounty or compensation.

| Severity | Acknowledgement | Triage target | Remediation target |
|---|---:|---:|---:|
| Critical | 4 hours | 8 hours | Containment immediately; fix or kill switch within 24 hours |
| High | 1 business day | 2 business days | 7 calendar days |
| Medium | 3 business days | 5 business days | 30 calendar days |
| Low | 5 business days | 10 business days | Planned release |

Critical examples include remote code execution, signing-chain compromise, authentication bypass, cross-user config access, payment/ledger manipulation, traffic or DNS leak affecting many users and loss of production data.

## Coordinated Disclosure

The Security Owner assigns a tracking ID, validates the report, establishes severity and keeps the reporter informed at material transitions. Public disclosure waits until affected supported versions are remediated and users have had reasonable time to update. Advisory credits are included only with the reporter's permission.

## Repository and Build Rules

- Production secrets and runtime databases never enter Git, Actions variables, build logs or artifacts.
- APK/AAB files, bot/server archives, receipts, raw logs, `google-services.json`, signing keys and local configuration are blocked from commits.
- Build credentials live in a protected secret manager or GitHub environment and are available only to the release job.
- Pull requests run repository guard, secret scan, dependency review, Android tests/lint/build and applicable static analysis.
- Release artifacts require checksums, SBOM, immutable version/tag, signing evidence and approval from a release owner.
- Security-sensitive changes require two reviewers across Security and the owning engineering discipline.
- No manual, QR, clipboard or file-based VPN config import/export may be introduced.

## Data and Logging Boundaries

Never log or attach raw VPN profiles, private keys, access/refresh tokens, Telegram authorization data, payment credentials, full IP addresses, DNS queries, destinations or traffic contents. Stable identifiers must be pseudonymous and environment-scoped. Diagnostic reports must be allowlisted, redacted, time-bound and visible to the user before upload.

Security telemetry may record event type, pseudonymous account/device key, coarse region, app/build version, error code, server identifier, decision, timestamp and correlation ID. Retention and access are documented in the observability runbook.

## Suspected Secret Exposure

1. Do not paste the value into an Issue, chat, log or pull request.
2. Revoke or rotate it at the issuing provider immediately.
3. Disable affected sessions, devices or release jobs when abuse is possible.
4. Preserve metadata and audit evidence without copying the secret.
5. Open a private advisory and follow the incident-response runbook.
6. Purge Git history only after rotation; history rewriting is not a substitute for revocation.

## Supply-Chain Policy

Dependencies must be versioned, license-reviewed and present in the release SBOM. High or critical known vulnerabilities block release unless Security records a time-bounded, compensating-control exception. Xray and native libraries must have a pinned upstream revision, verified checksum, provenance record and documented license. Generated artifacts are never accepted as substitutes for auditable source.

## Safe Harbor

Good-faith research following this policy is welcome. Avoid privacy violations, service degradation, social engineering, physical attacks and access beyond the minimum necessary to demonstrate impact. Stop and report immediately if sensitive data is encountered.
