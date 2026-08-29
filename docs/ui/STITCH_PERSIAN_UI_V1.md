# Stitch Persian UI V1

This branch ports the approved Stitch visual direction into the real Android Compose application.

## Scope
- Persian-only RTL app shell
- Emerald / gold Liquid Glass tokens from the approved Stitch design
- Floating 5-tab navigation with elevated center Connect action
- Production-wired Connect screen using real `GanjUiState`
- Honest placeholders for ping/download/upload until telemetry exists in the domain model
- Responsive Connect orb sizing for compact and large phones
- Existing VPN connection, server selection, subscription and checkout behavior remains intact

## Design source
The implementation follows the supplied Stitch export (`DESIGN.md`, `code.html`, and screen reference) while preserving the repository's existing architecture and security boundaries.

## Follow-up
The same component language should next be applied to Servers, Store, Home, and Profile without duplicating component styles.
