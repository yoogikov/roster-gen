# Gap list

What is missing, behind plan, or different from the design doc. Hiding a gap is worse
than declaring it; keep this current.

| # | Gap | Impact | Plan |
|---|---|---|---|
| 1 | About 3 weeks behind the design-doc milestones: the 11 Sep and 25 Sep deliverables (`domain`, frozen interfaces, `rules`, login/`AuthorizationPolicy`) did not exist on 30 Sep. | Mid-demo scope is reduced. | Phased catch-up plan starting 30 Sep. |
| 2 | Real July 2026 roster from the CMO not received. Scope changed on 11 Oct: the app's input is a list of doctors and a list of rules, and it generates the roster. There is no import of an existing roster sheet. | No check of our rules against a roster the hospital actually used. | If the CMO sends a filled-in sheet, use it only to test the rules. Tushar: the import endpoint in the plan's demo script goes. |
| 3 | The rule list has not been written down and confirmed by the CMO. | Constraints may be wrong. | Write it down, get it confirmed on paper; unconfirmed rules are SOFT. |
| 4 | `rules` (user-authored rule shapes) moved after the mid-demo. | Departs from the design doc. | Build in the 10–23 Oct phase. |
| 5 | Proposal PDF was built from a mostly unfilled template (prior work, tech stack, verification plan, milestones, risks). | Evaluator may ask. | Too late to resubmit; answer from the design doc. |
| 6 | Module owners not in the design doc; the split in `README.md` is proposed, not confirmed. | | Confirm and record. |
| 7 | Layer rules are documented in `package-info.java` but not enforced by a test. | A wrong-direction import would not be caught. | Consider an architecture test. |
| 8 | Hosting for the final demo not decided. | | Decide by about 20 Oct. |
| 11 | `domain` and the core interfaces landed on 11 Oct, 8 days after the plan's 3 Oct date. They blocked `repo`, `service`, `requests` and `AuthorizationPolicy`. | Tushar's persistence, edit and swap work could not start. | Merge `feat/domain-model` first so the blocked work can start. |
| 12 | No `NoOverlapConstraint`, although the plan lists one. `Roster` already refuses to hold an overlapping (double-booked) assignment, so such a constraint could never report anything. | An edit that double-books someone fails with `IllegalArgumentException` from `Roster`, not a `Violation`; `applyEdit` must turn that into a rejected edit. | Agree with Tushar how `applyEdit` reports it. |
| 13 | `MinRestGapConstraint` only compares shifts inside one month's roster, so a night on the last day of the previous month is not checked against the 1st. | A short rest across a month boundary goes unreported. | Pass the previous month's last shifts in once `repo` can load them. |
| 14 | Hard-constraint parameters (staffing levels, rest hours, leave rules) are not confirmed by the CMO. Each constraint takes its `Severity` as a parameter, so unconfirmed ones can run as SOFT. | Wrong numbers would block valid rosters if run as HARD. | Confirm with the CMO; until then, the caller chooses the severity. |
| 15 | `GreedyRosterGenerator` has no repair pass yet (design doc: small swaps to lower the SOFT penalty). Once placed, an assignment is never moved, so a slot can be left unfilled even when moving someone else would free a doctor for it. | Possible `Infeasible` results on tight months. | Repair pass next, before 23 Oct. |
| 16 | No loader yet for the doctor list and rule list. Tests build them in code. | Nothing for the CMO to type into yet. | Doctor CSV plus `rules` templates (`ConstraintFactory`). |
| 17 | Rule details we assumed, not yet confirmed by the CMO: leave of exactly 10 days counts as short (swap); a doctor back from long leave does the night on their return day; the weekly mornings/afternoons block is SOFT (her sheet breaks it on night days). | Rotation or weekly pattern may differ from what she expects. | Confirm with the CMO; each is one constant or one severity to change. |
| 18 | Left out on purpose for now: the 10:00-16:00 weekend duty, consultants on fixed days, and a doctor leaving partway through the month. | The generated roster has no 10-4 row and cannot end someone's service mid-month. | Add when needed. |
| 19 | Generating a month of 11 doctors takes about 6 s, because every candidate re-checks all rules on the whole roster. | Fine for a monthly job; slows the test suite. | Check only the days a candidate touches, if it becomes a problem. |
| 20 | Fairness is only by total shifts. Ward and casualty duties are not shared evenly (synthetic October: one doctor has 11 casualty shifts, another 2), and the weekly mornings/afternoons pattern is only partly kept (penalty 20). | The CMO may see the roster as unfair. | SOFT constraints for post balance, and a repair pass (gap 15). |
