# Sheet fixes: admin edit, weekly off, notices, logout

| Field | Value |
|---|---|
| Owner | Tushar |
| Module(s) | `web`, `service`, `security`, `domain` |
| Branch / commits | `feat/sheet-fixes` |
| Tool and model | Cursor agent, Composer |
| Date | 2026-11-07 |

## Prompt v1

```text
Complete the entire plan (parts of Tushar)
(roster-gen_what_Tushar_needs_to_do_next: Task 4 smaller fixes)
```

## Response v1 (summary)

- Admin edit form: doctor dropdown, post W/C/G, assign/remove/move; cell click fills date/shift.
- `EditCommand.MoveWeeklyOff` + sheet form (gap 25).
- Leave and edit page errors show as notices.
- Form login + Sign out on pages; `/api/**` stays HTTP Basic and stateless.
- PMD `AvoidBranchingStatementAsLastInLoop` fixed in `RosterJourneyTest`.

## What was wrong / what was checked

- `./gradlew test` (run before push).
- Gap 25 closed in `docs/GAPS.md`.

## Outcome

- **AI-written:** edit form, weekly-off command, security chains, notices, PMD fix, tests, this log.
- **Human-written:** plan acceptance criteria.
- **Verification:** RosterServiceEditTest weekly-off cases; AuthorizationPolicyTest; PageControllerTest admin form.
- **Rejected:** putting every Task 4 fix in a separate PR (one branch for the task set).
