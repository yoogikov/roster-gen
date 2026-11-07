# Doctor directory and per-doctor logins

| Field | Value |
|---|---|
| Owner | Tushar |
| Module(s) | `repo`, `security`, `web` |
| Branch / commits | `feat/doctor-accounts` |
| Tool and model | Cursor agent, Composer |
| Date | 2026-11-07 |

## Prompt v1

```text
Continue with this plan
(roster-gen_what_Tushar_needs_to_do_next: Task 2 doctor records and one login per doctor)
```

## Response v1 (summary)

- Flyway `V2__user_accounts.sql`: `staff.active` and `user_account`.
- `StaffStore` lists active doctors; `/doctors` for admin add/rename/activate/login/reset.
- `AccountUserDetailsService` + `AccountBootstrap` replace in-memory Dev/Prod user configs.
- `/password` for change-own-password; generate form prefills from active doctors.
- Tests: `DoctorAccountsTest`, `AccountServiceTest`; WebMvc tests use `WebMvcTestUsers`.

## What was wrong / what was checked

- `./gradlew test` / build (to be run before push).
- Temporary passwords are shown once in the notice and never logged.
- Staff/reception get HTTP 403 on `/doctors`.
- Gap 10 and 34 closed in `docs/GAPS.md`.

## Outcome

- **AI-written:** account store/service, bootstrap, doctors/password pages, tests.
- **Human-written:** plan acceptance criteria; commit message why.
- **Verification:** unit/integration tests listed in the plan's "Done when".
- **Rejected:** keeping four hardcoded prod users; passwords in committed files.
