# Swap state machine with lapse and night-ward moves

| Field | Value |
|---|---|
| Owner | Tushar |
| Module(s) | `requests`, small change to `service` (`applyEdits`) |
| Branch / commits | `feat/swap-requests` / "Add swap state machine with lapse and night-ward moves" |
| Tool and model | Cursor agent, Grok 4.7 |
| Date | 2026-11-04 |

## Prompt v1

```text
I'll give the plan
Also senior staff is not to be committed
Strictly follow the plan only
Make next two commits (feel free to make new branches if specified in the plan)
```

The plan for this commit is Tue 6 Oct in `PROJECT-PLAN.md`: `feat/swap-requests`, the swap
state machine in `requests` (propose, accept, approve or reject; PENDING or ACCEPTED at
shift start becomes LAPSED, with an injected `Clock`). Approval goes through `applyEdit`
and writes 2 audit events. Plus `SwapLifecycleTest`.

The handover (11 Oct) adds two rules this commit also follows: a swap that moves a night
must also move that day's Shift I ward duty, and the post-duty off moves with it; a
short-leave night swap follows `NightRotation` (at most 10 days: the night goes to the
next doctor who is free). The untracked senior-staff log was not committed.

## Response v1 (summary)

- `SwapLifecycle`: propose → accept → approve or reject. Approve calls
  `RosterService.applyEdits`. Lapse marks open requests LAPSED when the earlier of the
  two shifts has started, including that exact instant, and does not touch the roster.
- A night move also moves that day's Shift I ward duty. Post-duty off is not an
  assignment; `PostDutyOffConstraint` follows whoever has the night.
- If the doctor giving away a night is on short leave that day, the doctor taking it
  must be the one `NightRotation.plan` names. A long leave is not treated this way.
  A month with no stored input is not checked.
- `applyEdits` removes first and adds afterwards, then checks double-booking and the
  hospital rules on the final roster. One audit event per command, and only if the
  whole batch is saved. `applyEdit` delegates to it.
- `SwapLifecycleTest` (13) and two `applyEdits` cases in `RosterServiceEditTest`.

## What was wrong / what was checked

- **The first post-duty-off test swapped away the new night doctor's only shift on the
  next day**, so the off day moved with them and the edit was allowed. The shift on the
  off day is now a third assignment that the swap does not touch.
- **Why `applyEdits`:** moving the night and the ward as two `applyEdit` calls rejects
  the first, because `NightIncludesMorningWardConstraint` fails in the middle. The same
  happens when exchanging Shift I and Shift II, which overlap for 30 minutes.
  Recorded as GAPS 28. GAPS 27 records that `requests` calls `service` and `security`.
- **Hand mutation:** lapse changed to `now.isAfter(start)`, so a request does not lapse at the exact start. `openRequestLapsesAtTheEarlierShiftStartAndTheRosterIsUntouched` failed. Reverted.
- `./gradlew test --tests SwapLifecycleTest --tests RosterServiceEditTest`: pass.
  `spotbugsMain` on the reverted sources: no findings. PMD on those sources was clean before the mutation edit.

## Outcome

- **AI-written:** `SwapLifecycle`, `SwapResult`, `applyEdits`, the tests, GAPS rows
  26–28, this log.
- **Human-written:** none of the files. Tushar wrote the prompt and said to follow the
  plan, and not to commit the senior-staff work.
- **Verification:** as listed above. Human line-by-line review still to do before the PR.
- **Rejected:**
  - one `applyEdit` per assignment (GAPS 28)
  - storing post-duty off as a row (the handover says it is never stored)
  - lapsing only the offered shift, ignoring a wanted shift that starts sooner
- **Not done:** REST API, the scheduled lapse job, `demo/mid-demo.sh` (GAPS 26).
  `RequiresApproval` is still not stored as a request (GAPS 24).
