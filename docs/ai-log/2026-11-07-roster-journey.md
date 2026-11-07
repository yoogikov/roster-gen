# RosterJourneyTest and local Postgres deploy

| Field | Value |
|---|---|
| Owner | Tushar |
| Module(s) | `web`, `service`, `security`, `repo` |
| Branch / commits | `feat/phase-d` / "Add RosterJourneyTest covering generate, edit, swap and export" |
| Tool and model | Cursor agent, Grok 4.6 |
| Date | 2026-11-07 |

## Prompt v1

```text
make the last two commits too and merge all the branches to final state
(after this prompt, project from my end (Tushar) should be complete)
```

Phase D for Tushar in `PROJECT-PLAN.md`: deploy with Postgres, and
`RosterJourneyTest` (`@SpringBootTest`): author a rule, generate, edit, swap,
export, then check the exact audit sequence. Hosting stays a documented local
Docker Compose Postgres (gap 29). PDF export stays Yoogi's.

## Response v1 (summary)

- Flyway `V1__hospital_roster.sql` is the Hibernate Postgres create script,
  reviewed and checked in. Prod enables Flyway and `ddl-auto=validate`.
  `ProdPostgresTest` now starts that way instead of `ddl-auto=update`.
- `ProdUsersConfig` gives the same four synthetic logins as `dev`, with
  `ROSTER_ADMIN_PASSWORD` required. `demo/run-prod.sh` brings up Compose and
  the jar.
- `RosterJourneyTest` posts a post-duty-off rule, generates December 2026
  (six synthetic doctors), assigns an extra Shift II GENERAL, publishes,
  swaps D01 with D06, downloads CSV, and checks five audit summaries in order.
- `RuleBook` can save a template before a month has been generated.

## What was wrong / what was checked

- Schema dump used embedded Postgres so the script matches validate, not H2.
- The journey clock is fixed at 7 Nov 2026 so December swaps are not past.
- `docs/ai-log/2026-10-12-senior-staff.md` was left untracked.
