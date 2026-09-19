# SnapSell

Monorepo: `backend/` (FastAPI), `ios/` (SwiftUI, XcodeGen), `android/` (Compose), `contracts/` (JSON Schema shared by all three), `deploy/` (VPS compose), `docs/` (decisions).

## Playbooks

Operational procedures live in `playbooks/` and are the source of truth for how
this repo is deployed, released, extended, and opened to testers. Before doing
any of those, read the matching playbook and follow it; `.claude/skills/` only
points at them. Steps tagged `[HUMAN]` mean stop and ask. Fix a wrong playbook
in the same PR as the fix.

## Conventions

- Contracts first: a payload change starts in `contracts/`, then backend, iOS, Android, each with its contract test.
- Decisions in `docs/DESIGN.md` are not reopened silently; add a row when one changes.
- PRs are squash-merged after CI is green.
