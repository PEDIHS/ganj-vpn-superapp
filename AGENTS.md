# Ganj VPN — Mandatory Agent Instructions

These instructions apply to every AI agent, automated coding agent, and developer modifying this repository.

## Mandatory reading before changes

1. `README.md`
2. `docs/00-CURRENT_PRODUCT_SCOPE.fa.md`
3. `docs/PROJECT_BIBLE.fa.md`
4. `docs/AI_ENGINEERING_HANDOFF.fa.md`
5. **`docs/17-repository-branch-pr-governance.fa.md`**
6. The implementation and tests for the affected module.

For Android UI work, also read:

- `docs/15-android-ui-ux-brand-system.fa.md`
- `docs/16-liquid-glass-first-design-standard.fa.md`
- `design/tokens.json`

## Repository governance is mandatory

The repository follows trunk-based development with short-lived branches.

- `main` is the only long-lived source-of-truth branch.
- Before creating a branch, search existing open PRs and related branches.
- Do not create duplicate parallel branches for the same scope.
- New work should normally use `feat/`, `fix/`, `docs/`, `refactor/`, `test/`, `chore/`, or `hotfix/` naming.
- Do not continue the historical `phase-N/...-v2/v3/v4` pattern unless there is a documented exceptional reason.
- Normal feature work must go through a Pull Request and required CI/security gates.
- Prefer Squash Merge for normal feature/fix/docs PRs.
- After merge, delete the head branch.
- After superseding a PR/branch, preserve any required unique commits, close the predecessor with a clear reason, then delete the stale branch.
- Never use branches as permanent archives; Git/PR history is the archive.
- Never blindly merge a stale branch. Compare it with current `main`; fresh-port only the required changes when necessary.
- Never force-push or rewrite another active agent's branch unless explicitly assigned to do so.

The complete binding policy and checklists are in `docs/17-repository-branch-pr-governance.fa.md`.

## Non-negotiable product/security boundaries

- No manual VPN URI/QR/file/clipboard/subscription-URL import in production.
- No production secrets, tokens, private keys, raw VPN profiles, or purchase tokens in repository/logs/UI state.
- Do not weaken fail-closed behavior to make tests pass.
- Do not claim tests, deployment, provider verification, or production readiness without actual evidence.
- Do not modify already-merged database migrations; add a new migration.
- Do not merge with required CI/security gates failing or unknown.

## Handoff rule

Every contribution must leave the repository easier for the next agent to understand: focused PR, accurate Remaining Boundary, synchronized docs/contracts, and no unnecessary stale branch left behind.