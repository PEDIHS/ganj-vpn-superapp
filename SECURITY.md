# Security Policy

Ganj VPN handles VPN credentials, account identities and payment entitlements. Do not open a public issue containing a config URI, UUID/password, bot token, refresh token, provider credential, receipt or user record.

## Reporting

Until a dedicated security mailbox is configured, report privately to the repository owner through a private GitHub security advisory. Include affected version, reproduction steps, impact and a minimal proof without real user data.

## Repository Rules

- No production secret or runtime database in Git.
- No APK, bot ZIP, receipt, error log or `google-services.json` in commits.
- Rotate exposed credentials; deleting a commit is not sufficient.
- Security-sensitive changes require two reviewers, including Security or Mobile Architecture.
- Release tags and artifacts must be signed and checksummed.

## Scope Priorities

Highest priority: remote code execution, authentication bypass, config disclosure, traffic leak, payment/ledger manipulation, signing-chain compromise and cross-user data access.

