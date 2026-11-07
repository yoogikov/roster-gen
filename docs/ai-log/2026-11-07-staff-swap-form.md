# Staff swap and change forms on the sheet

| Field | Value |
|---|---|
| Owner | Tushar |
| Module(s) | `web`, `requests`, `repo` |
| Branch / commits | `feat/staff-swap-form` |
| Tool and model | Cursor agent, Composer |
| Date | 2026-11-07 |

## Prompt v1

```text
Complete the entire plan (parts of Tushar)
(roster-gen_what_Tushar_needs_to_do_next: Task 3 swap and change requests from the browser)
```

## Response v1 (summary)

- Staff forms on a published sheet: propose a swap and request removal of own shift (dropdowns, no typed ids).
- Inbox "My requests" with state for swaps, changes and leave.
- Readable notices use doctor names and dates instead of request UUIDs.
- Store queries for requests that involve a staff id; `RequestInbox.mine`.
- Tests: `StaffSwapFormTest` (propose/accept/approve from the page, change request, refuse other's shift, reception read-only).

## What was wrong / what was checked

- `./gradlew test --tests StaffSwapFormTest --tests PageControllerTest`: BUILD SUCCESSFUL.
- Reception and draft sheets stay without staff forms.
- Stacked on `feat/doctor-accounts` (PR #13) until that merges.

## Outcome

- **AI-written:** sheet forms, inbox mine section, store queries, notices, tests, this log.
- **Human-written:** plan acceptance criteria.
- **Verification:** StaffSwapFormTest Done-when path; PageControllerTest still green.
- **Rejected:** changing the admin cell-edit UI in this task (Task 4).
