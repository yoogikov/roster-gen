# Gap list

What is missing, behind plan, or different from the design doc. Hiding a gap is worse
than declaring it; keep this current.

| # | Gap | Impact | Plan |
|---|---|---|---|
| 1 | About 3 weeks behind the design-doc milestones: the 11 Sep and 25 Sep deliverables (`domain`, frozen interfaces, `rules`, login/`AuthorizationPolicy`) did not exist on 30 Sep. | Mid-demo scope is reduced. | Phased catch-up plan starting 30 Sep. |
| 2 | Real July 2026 roster from the CMO not yet confirmed as received. | Mid-demo "real input" criterion depends on it. | Request it from the CMO now; synthetic fixtures meanwhile. |
| 3 | The rule list has not been written down and confirmed by the CMO. | Constraints may be wrong. | Write it down, get it confirmed on paper; unconfirmed rules are SOFT. |
| 4 | `rules` (user-authored rule shapes) moved after the mid-demo. | Departs from the design doc. | Build in the 10–23 Oct phase. |
| 5 | Proposal PDF was built from a mostly unfilled template (prior work, tech stack, verification plan, milestones, risks). | Evaluator may ask. | Too late to resubmit; answer from the design doc. |
| 6 | Module owners not in the design doc; the split in `README.md` is proposed, not confirmed. | | Confirm and record. |
| 7 | Layer rules are documented in `package-info.java` but not enforced by a test. | A wrong-direction import would not be caught. | Consider an architecture test. |
| 8 | Hosting for the final demo not decided. | | Decide by about 20 Oct. |
| 9 | `AuthorizationPolicy` and `Decision` not written yet. They must be hand-written and depend on `domain` types (`Actor`, `Action`, `Roster`) that have not landed. Only authentication exists; no per-action authorization. | No role checks on actions yet. | Tushar writes them by hand once `feat/domain-model` merges. |
| 10 | The `prod` profile has no user store; only `dev` has (synthetic, in-memory) users. | Prod falls back to Spring Boot's single generated user. | Users backed by `repo` with the persistence work. |
| 11 | `domain` and the core interfaces landed on 11 Oct, 8 days after the plan's 3 Oct date. They blocked `repo`, `service`, `requests` and `AuthorizationPolicy`. | Tushar's persistence, edit and swap work could not start. | Merge `feat/domain-model` first so the blocked work can start. |
| 17 | No schema migration tool. `dev` and tests let Hibernate create the schema; `prod` only validates it, so nothing creates the Postgres tables yet. Persistence has only been run on H2. | First prod start on an empty Postgres fails validation. | Add Flyway (or a reviewed `schema.sql`) with the hosting work and test against a real Postgres. |
| 18 | Assignment order on reload relies on the `list_index` column. On H2, a test cannot tell whether that column is there, because H2 returns rows in insertion order anyway. | Removing `@OrderColumn` would pass every test but could reorder rosters on Postgres. | Re-run the round-trip tests on Postgres (gap 17). |
| 19 | The running app has no constraints yet: the default `ConstraintSource` is empty, so `applyEdit` only refuses double-booking. Tests use fake constraints. | Edits in the running app are not checked against staffing, leave or rest rules. | `rules` (or a fixed list built from `constraints`) provides a `ConstraintSource` bean. |
| 20 | `applyEdit` does not ask `AuthorizationPolicy` (gap 9) and treats published and draft rosters alike. | Any authenticated user who can call it could change a published roster. | Call the policy from `applyEdit` once it is hand-written. |
| 21 | Found on Yoogi's unmerged `feat/greedy-generator` on 11 Oct: with 3 doctors and a minimum of 2 per morning, `GreedyRosterGenerator` returns `Infeasible` with all 62 July slots unfilled. Its "no new HARD violation" check compares exact violations, and `MinStaffingConstraint`'s message changes from "has 0" to "has 1" as a slot fills. | Any shift needing 2 or more people is never filled. | Yoogi to fix before merging. `RosterService` compares counts per constraint and date for this reason. |
