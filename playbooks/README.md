# Playbooks

A playbook is a written, versioned procedure that any agent (Claude Code,
Codex, a person) executes the same way every time. The files here are the
source of truth; `.claude/skills/<name>/SKILL.md` are one-line shims so
`/deploy-backend` and friends work in Claude Code.

| Playbook | When |
|---|---|
| [`deploy-backend.md`](deploy-backend.md) | A backend or `deploy/` change is merged to `main` and must go live on the VPS. |
| [`release-ios.md`](release-ios.md) | An iOS build must reach a phone: direct-to-device now, TestFlight once configured. |
| [`release-android.md`](release-android.md) | An Android build must reach a phone: CI publishes on every push to `main`; this covers manual runs and verification. |
| [`add-endpoint.md`](add-endpoint.md) | A new payload crosses the network: schema, example, backend, iOS, Android, tests. |
| [`onboard-tester.md`](onboard-tester.md) | Someone new needs to sign in and use the app. |

## Template

Every playbook has exactly these sections:

1. **Goal**: one sentence, the observable end state.
2. **Preconditions**: what must already be true; each is checkable.
3. **Steps**: numbered, each is a command or a single action. Steps that need a
   credential the agent does not hold, or that are irreversible, are tagged
   `[HUMAN]`: the agent prints the exact command or action and stops until the
   human reports the result.
4. **Verification**: commands whose output proves the goal; the playbook is not
   done until they pass.
5. **Rollback**: how to return to the previous state.

## Conventions

- Paths are relative to the repository root unless absolute.
- Facts that change (hostnames, IDs, team) live in the **Facts** table at the top
  of each playbook. Update the table, not the steps.
- An agent that finds a step wrong fixes the playbook in the same PR as the fix.
- `[HUMAN]` means stop and ask; never work around it.
