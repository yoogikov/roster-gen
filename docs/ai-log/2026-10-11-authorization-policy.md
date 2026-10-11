# AuthorizationPolicy, Decision and Action

| Field | Value |
|---|---|
| Owner | Tushar |
| Module(s) | `security` |
| Branch / commits | `feat/security-basic` / "Add AuthorizationPolicy with Allow, Deny and RequiresApproval" |
| Tool and model | Cursor agent, Claude Opus 5.5 |
| Date | 2026-10-11 |

**Departure from the plan.** `PROJECT-PLAN.md`, the mid-demo disclosure and Yoogi's
handover all said Tushar would write `AuthorizationPolicy`, `Decision` and `Action` by
hand. He asked the agent to write them instead. This log records that, and
`docs/disclosure/mid-demo.md` and GAPS row 9 have been corrected to match.

## Prompt v1

```text
AuthorizationPolicy, Decision and Action -> Write these in a new commit
```

Context the agent used: the design doc's signature
`Decision may(Actor actor, Action action, Roster target)` with
`Allow | Deny(reason) | RequiresApproval(Role approver)`, and its hard cases (editing a
published roster, moving someone else's shift, changing past dates).

## Response v1 (summary)

- `Decision`: sealed; `Allow`, `Deny(reason)` (reason must not be blank),
  `RequiresApproval(approver)`.
- `Action`: sealed; `ViewRoster`, `GenerateRoster`, `PublishRoster`,
  `EditShift(slot, staffIds)`, `ProposeSwap(offered, wanted)`, `AcceptSwap(request)`,
  `DecideSwap(request)`. The month comes from the target roster.
- `AuthorizationPolicy`: the design-doc interface, unchanged.
- `HospitalAuthorizationPolicy`: the rules, as a table in its Javadoc. In short:
  - ADMIN may do anything except regenerate a published month, accept someone else's
    swap, or decide a swap whose shift has passed
  - only ADMIN edits a draft or a past date
  - on a published roster, a RECEPTION edit needs ADMIN approval, and a STAFF edit
    needs ADMIN approval if it changes only their own shifts; otherwise it is denied
  - swaps are only for a published roster and future shifts; STAFF may only offer their
    own shift, and only the person asked may accept
  - "past" is before today in `Asia/Kolkata`, from an injected `Clock`
- `AuthorizationPolicyTest`: 17 tests, made-up doctors `D01` and `D02`.

## What was wrong / what was checked

- **PMD `UnusedLocalVariable`** on the switch's unused pattern variables. Fixed with
  record patterns. For `DecideSwap` the request is now used to refuse a past swap,
  rather than suppressing the finding.
- **SpotBugs `CT_CONSTRUCTOR_THROW`** (constructor null check in a non-final class).
  Fixed by making the class `final`.
- **The test clock is 01:30 IST on 11 Oct, which is still 10 Oct in UTC,** so a policy
  that ignores the hospital zone fails `pastDatesAreOnlyForAdminEvenWhenPublished`.
- **Hand mutation testing, each break caught by `AuthorizationPolicyTest`:**
  - "today" taken in UTC instead of `Asia/Kolkata`
  - "past" includes today
  - staff may edit anyone's shift
  - no draft check
  - published month may be regenerated
  - reception edits a published roster directly
  - staff may offer someone else's shift
  - anyone may accept a swap
  - a past swap may be decided (the first version of this mutant left half the
    condition in place and survived; it was redone)
- `./gradlew clean build`: 182 tests, 0 failures, 0 PMD, 0 SpotBugs.

## Outcome

- **AI-written:** all four types, the test, GAPS rows 9 and 24, the mid-demo disclosure
  correction, and this log.
- **Human-written:** none of the files. Tushar wrote the prompt and chose to have these
  written by the AI.
- **Verification:** as listed above. Human line-by-line review still to do before the
  PR; Tushar should be able to explain every rule in the table at the viva.
- **Not done:** nothing calls the policy yet. Wiring it into `applyEdit` and generation,
  and turning `RequiresApproval` into a pending request, is GAPS row 24. Dev users
  `staff1` and `staff2` are not linked to a staff id, so for now they can view a roster
  but are refused any edit or swap.
- **Rejected:**
  - checking roles in `SecurityConfig` URL rules instead (no web endpoints yet, and
    URL rules cannot see the roster's status or dates)
  - letting STAFF edit their own published shifts directly (the plan routes staff
    changes through approval)
