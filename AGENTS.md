# Ganj VPN — Mandatory Agent Instructions

These instructions apply to every AI agent, automated coding agent, and developer modifying this repository.

## Mandatory reading before changes

1. `README.md`
2. `docs/00-CURRENT_PRODUCT_SCOPE.fa.md`
3. `docs/PROJECT_BIBLE.fa.md`
4. `docs/AI_ENGINEERING_HANDOFF.fa.md`
5. **`docs/17-repository-branch-pr-governance.fa.md`**
6. The implementation and tests for the affected module.

For **any Android UI/UX work**, the following order is mandatory and supersedes generic UI guidance:

1. **`docs/ui/UI_100_PERCENT_PROGRESS.fa.md`** — read the entire ledger first.
2. **Run `node scripts/ui-progress.mjs --sections` and treat its Sections 1–26 result as the only valid UI percentage.**
3. `docs/15-android-ui-ux-brand-system.fa.md`
4. `docs/16-liquid-glass-first-design-standard.fa.md`
5. `design/tokens.json`
6. Current runtime routes/screens, related open PRs/issues, and tests.

## Mandatory UI progress protocol

Every UI Agent MUST:

- inspect the latest `main`, active branch and related PRs/issues before coding;
- run `node scripts/ui-progress.mjs --sections` before coding and again after updating the ledger;
- select work from unchecked `[ ]` leaf items in `docs/ui/UI_100_PERCENT_PROGRESS.fa.md`;
- complete items one-by-one, including subpages, dialogs, bottom sheets, popups, loading, empty, offline, permission, error, success, retry, responsive and accessibility states;
- update the ledger in the SAME PR/commit batch as the implementation;
- change `[ ]` to `[x]` only when the leaf item satisfies the ledger Definition of Done;
- never mark a parent item complete while any required child remains incomplete;
- never use mock/placeholder/fake telemetry, wallet, transaction, account, server or billing data to claim completion;
- never expose an action that has no real runtime/controller/backend handler unless it is explicitly documented as a non-interactive preview;
- preserve fail-closed security/product behavior when wiring UI;
- record remaining unchecked items and the **calculator-produced** completion percentage after each batch.

### UI percentage formula — mandatory

Manual or weighted UI percentages are prohibited. The repository calculator counts only executable checkbox items in Ledger Sections **1 through 26**:

```text
UI Progress = checked executable leaf items / all executable leaf items × 100
```

Section 0 process rules and Section 27 duplicated final release gates are not part of the progress percentage. An item may leave the denominator only after a documented canonical product-scope decision formally defers/removes it. External blockers such as physical-device QA remain in the denominator while required.

Required UI batch summary format:

```text
UI Ledger sections touched: <IDs>
Leaf items completed: <list>
Remaining unchecked items in touched sections: <count/list>
Overall UI completion: <calculator output>% (100% forbidden until Final Gate is all checked)
```

**100% UI is forbidden to claim until every required Final 100% Release Gate checkbox in the ledger is checked with real build/device/runtime evidence.** Deleting checklist items to hide remaining work is prohibited. If product scope changes, update canonical scope docs first, then mark the ledger item as formally deferred with the scope decision; do not silently skip it.

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

Every contribution must leave the repository easier for the next agent to understand: focused PR, accurate Remaining Boundary, synchronized docs/contracts, synchronized UI progress ledger for UI work, and no unnecessary stale branch left behind.
