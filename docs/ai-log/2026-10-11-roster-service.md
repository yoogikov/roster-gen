# RosterService.applyEdit with audit

| Field | Value |
|---|---|
| Owner | Tushar |
| Module(s) | `service` |
| Branch / commits | `feat/security-basic` / "Add RosterService.applyEdit with constraint checks and audit" |
| Tool and model | Cursor agent, Claude Opus 5.5 |
| Date | 2026-10-11 |

## Prompt v1

```text
Make two more commits according to plan for me (Tushar). Again, they should be on my branch
```

Same session as [the persistence log](2026-10-11-persistence.md). Plan task (Mon 5 Oct,
`feat/roster-service`): "`RosterService.applyEdit`, which is `@Transactional`, runs every
`Constraint`, rejects HARD violations, returns SOFT warnings, and writes exactly one
`AuditEvent`. Plus `RosterServiceEditTest`." Also Yoogi's GAPS row 12 on his branch: no
`NoOverlapConstraint`, so `applyEdit` must turn double-booking into a rejected edit.

## Response v1 (summary)

- `EditCommand` (sealed: `Assign`, `Unassign`, `Move`, staff given by id) and
  `EditResult` (sealed: `Applied(roster, warnings)`, `Rejected(violations)`).
- `ConstraintSource` (constraints for a month) with an empty default bean, and a `Clock`
  bean in `Asia/Kolkata`, in `ServiceConfig`.
- `RosterService.applyEdit`: loads the month, checks double-booking itself (reported as a
  HARD `double-booking` violation), runs the month's constraints before and after, rejects
  if any constraint and date has more HARD violations than before, else saves the roster
  and appends one audit event in the same transaction. Unknown staff, no roster, or
  removing someone not on the slot throw `IllegalArgumentException` (bad input, not a rule).
- `RosterServiceEditTest` (14) with `FakeConstraints`; `RosterServiceTransactionTest` (2).

## What was wrong / what was checked

- **Bug found by the tests:** the first `added(...)` returned every violation for a
  constraint and date whose count rose, so one new SOFT warning came back as two (the old
  one too). Fixed by first removing, one-for-one, the violations already present before.
- **Design choice, checked against Yoogi's code:** comparing exact violations (as his
  generator does) rejects valid edits, because `MinStaffingConstraint`'s message includes
  the current count. A probe test in a throwaway worktree of `feat/greedy-generator`
  confirmed the generator leaves all 62 slots unfilled for a minimum of 2 (GAPS row 21).
  The probe was not committed. `editThatImprovesAnExistingHardViolationIsAllowed`
  covers this case for `applyEdit`.
- SpotBugs `EI_EXPOSE_REP2` on storing the injected `AuditStore`: false positive for a
  Spring singleton. Suppressed with `@SuppressFBWarnings` plus a justification, which
  needed `spotbugs-annotations` 4.10.4 as `compileOnly` (same artifact and licence,
  LGPL-2.1, already used by tests; not packaged in the jar).
- Hand mutation testing of `RosterService`, each killed:
  - reject on any HARD violation: `editThatImprovesAnExistingHardViolationIsAllowed`
  - drop the double-booking check: 3 tests
  - remove `@Transactional`: `failureWritingTheAuditEventRollsBackTheRosterChange`
    (forces the audit insert to fail with a 300-character actor name)
- `./gradlew clean build`: 73 tests (16 new), 0 failures, 0 PMD, 0 SpotBugs.

## Outcome

- **AI-written:** all of `service` (besides the original `package-info.java` text), both
  test classes and `FakeConstraints`, the `build.gradle` line, GAPS rows 19–21, this log,
  commit subject.
- **Human-written:** <!-- TODO: Tushar -->
- **Verification:** as listed above. Human line-by-line review still to do before the PR.
- **Rejected:** letting `Roster`'s `IllegalArgumentException` signal double-booking
  (exception as control flow, and it would hide other argument errors); requiring zero
  HARD violations after an edit; exact-match comparison of violations.
- **Departure:** `service/package-info.java` now also lists `constraints` and `domain`
  as allowed dependencies, since `applyEdit` checks each `Constraint` directly, as the design
  doc describes.
