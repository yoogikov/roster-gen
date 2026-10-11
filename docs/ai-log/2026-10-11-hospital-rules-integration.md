# Store posts, weekly offs and month input; generate a month

| Field | Value |
|---|---|
| Owner | Tushar |
| Module(s) | `repo`, `service` |
| Branch / commits | `feat/security-basic` / merge of `feat/hospital-duty-rules`, then "Generate and edit rosters under the hospital rules" |
| Tool and model | Cursor agent, Claude Opus 5.5 |
| Date | 2026-10-11 |

## Prompt v1

```text
Complete whatever is for me (Tushar) in at max 2 more commits
```

Attached: `roster-gen-handover-for-tushar.pdf` (Yoogi, 11 Oct). Its "What changes for your
modules" table: `repo` must save `Assignment.post` and `Roster.weeklyOffs`; `applyEdit`
should run `HospitalRosterGenerator.constraints(MonthInput)`; there is no import, and a
generate step takes the month input instead.

The agent used the two commits for: (1) merging `feat/hospital-duty-rules`, which contains
all four of Yoogi's branches, into `feat/security-basic`; (2) this work. Not done, and why:

- `AuthorizationPolicy`, `Decision`, `Action`: must be hand-written by Tushar (plan and handover).
- Swap state machine, REST API, demo script: would not fit in one reviewable commit (GAPS 26).

## Response v1 (summary)

- **Merge:** only `docs/GAPS.md` conflicted. Kept Yoogi's rows 12–20 and renumbered
  Tushar's 17–20 to 21–24. Dropped Tushar's old row 21 (generator bug), because Yoogi
  had fixed it in `5badb8f`. 151 tests green on the merge result.
- **`repo`:**
  - a `post` column on every stored assignment, including both sides of a swap request
  - a `roster_weekly_off` table
  - `MonthInputStore` with `StoredMonthInput`: a copy of `scheduler.MonthInput`'s
    fields, because `repo` may not depend on `scheduler`, which is in the same layer
- **`service`:**
  - `EditCommand.Assign` takes a `Post` (default general); a move keeps the post, and
    unassign finds the assignment whatever its post
  - double-booking now uses `Roster.wouldDoubleBook` from `domain`
  - the `ConstraintSource` bean builds the hospital rules from the month's stored input
  - new `RosterGenerationService.generate(MonthInput, Actor)` saves the doctors, the
    input, the roster (or the partial one) and one audit event; it refuses to regenerate
    a published month
- **Tests:**
  - `MonthInputStoreTest` (4, new) and `RosterGenerationServiceTest` (3, new; a synthetic
    October like Yoogi's, 11 made-up doctors)
  - new cases in `RosterRepositoryTest`, `SwapRequestStoreTest` and `RosterServiceEditTest`

## What was wrong / what was checked

- **Bug found by the tests:** saving a month's input again kept the old holidays.
  `holidays.clear()` + `addAll` on the element-collection `Set` produced no `DELETE`
  (checked with `spring.jpa.show-sql`). Replacing the set fixes it. Added tests that
  empty a non-empty leave list and weekly-off list on resave; both pass with clear-and-refill.
- **Checked the generated roster against the real rules through Spring:**
  - giving Monday's night doctor a Tuesday shift is rejected with `post-duty-off`
  - removing a general doctor from a weekday Shift II is rejected with `min-staffing`
  - in both cases the roster and the audit log are unchanged
- **Hand mutation testing, each break caught:**
  - posts not read back: 6 tests fail
  - empty `ConstraintSource`: `editsToAGeneratedRosterAreCheckedAgainstTheHospitalRules`
  - no published-roster guard: `publishedMonthIsNotRegenerated`
- **SpotBugs `EI_EXPOSE_REP2` on `RosterGenerationService`:** a false positive for
  injected singletons, as before. Suppressed with a justification.
- `./gradlew clean build`: 165 tests, 0 failures, 0 PMD, 0 SpotBugs.

## Outcome

- **AI-written:** everything in both commits (the GAPS conflict resolution, all new and
  changed code and tests, GAPS rows 12, 23, 25, 26, this log), plus the earlier logs'
  "Human-written" lines and row-number fixes that were still uncommitted.
- **Human-written:** none of the files. Tushar wrote the prompt and set the two-commit limit.
- **Verification:** as listed above. Human line-by-line review still to do before the PR.
- **Rejected:**
  - moving `MonthInput` into `domain` (it is Yoogi's `scheduler` type)
  - letting `repo` import `scheduler` (breaks the layer rule)
  - mixing the merge and the code changes in one commit
