# AI prompt log

One file per AI-assisted task, named `YYYY-MM-DD-task-slug.md`, written from
[`TEMPLATE.md`](TEMPLATE.md). Log as you go; do not rebuild the log at the end.

Rules:

- Prompts are copied verbatim and contain synthetic data only (no real staff names,
  phone numbers, shifts or leave, and no secrets).
- Each log states which parts are AI-written and which are human-written, how the AI
  parts were verified, and what was rejected.
- Each stage has a roll-up in `docs/disclosure/<stage>.md` that links to these logs.

## Index

| Date | Task | Owner | Commit(s) |
|---|---|---|---|
| 2026-09-30 | [Repository scaffolding](2026-09-30-repo-scaffolding.md) | Tushar | "Add project scaffolding, CI and docs structure" |
| 2026-10-06 | [HTTP Basic security and dev users](2026-10-06-security-basic.md) | Tushar | "Add HTTP Basic security config with synthetic dev users" |
| 2026-10-11 | [Persistence for staff, rosters, swaps and audit](2026-10-11-persistence.md) | Tushar | "Add JPA persistence for staff, rosters, swaps and audit" |
| 2026-10-11 | [RosterService.applyEdit with audit](2026-10-11-roster-service.md) | Tushar | "Add RosterService.applyEdit with constraint checks and audit" |
| 2026-10-11 | [Store posts, weekly offs and month input; generate a month](2026-10-11-hospital-rules-integration.md) | Tushar | "Generate and edit rosters under the hospital rules" |
| 2026-10-11 | [AuthorizationPolicy, Decision and Action](2026-10-11-authorization-policy.md) | Tushar | "Add AuthorizationPolicy with Allow, Deny and RequiresApproval" |
