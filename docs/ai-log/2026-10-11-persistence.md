# Persistence for staff, rosters, swaps and audit

| Field | Value |
|---|---|
| Owner | Tushar |
| Module(s) | `repo` |
| Branch / commits | `feat/security-basic` / "Add JPA persistence for staff, rosters, swaps and audit" |
| Tool and model | Cursor agent, Claude Opus 5.5 |
| Date | 2026-10-11 |

## Prompt v1

```text
Make two more commits according to plan for me (Tushar). Again, they should be on my branch
```

Context the agent used: `PROJECT-PLAN.md` (Sun 4 Oct task `feat/persistence`: "JPA
entities in `repo` (separate from the `domain` records), mappers between domain and
entities, Spring Data repositories (Staff, Roster, Request, Audit), and
`RosterRepositoryTest` (`@DataJpaTest`) for the save/reload round trip"), and Yoogi's
pushed but unmerged branch `feat/domain-model`. The agent merged that branch into
`feat/security-basic` first (separate merge commit; only `docs/GAPS.md` conflicted, both
sides kept) because every entity maps a `domain` record.

## Response v1 (summary)

- Entities `StaffEntity`, `RosterEntity` (assignments as an `@ElementCollection` of
  `AssignmentEmbeddable`, ordered by `list_index`), `SwapRequestEntity` (two embedded
  assignments with `offered_`/`wanted_` columns), `AuditEventEntity`. Each entity maps
  itself: a constructor or `update` from the domain record, and `toDomain()`.
- Package-private Spring Data interfaces; public `StaffStore`, `RosterStore`,
  `SwapRequestStore`, `AuditStore` that only take and return `domain` records.
- `RosterStore.save` replaces the month's roster. Every assigned staff member must
  already be stored with the same name and role, or it throws.
- Tests: `RosterRepositoryTest` (8), `StaffStoreTest` (4), `AuditStoreTest` (4),
  `SwapRequestStoreTest` (4); every reload goes through `flush()` + `clear()` so it is
  read back from the database, not the persistence context.

## What was wrong / what was checked

- Boot 4 moved the test annotations: `DataJpaTest` is in
  `org.springframework.boot.data.jpa.test.autoconfigure`, `TestEntityManager` in
  `org.springframework.boot.jpa.test.autoconfigure`. Checked in the resolved jars.
  Hibernate 7.4.5 has a `Duration` type but none for `YearMonth`, so months are stored as text.
- Column names avoid SQL keywords (`slot_date`, `roster_month`, `occurred_at`).
- PMD: 5 × `UncommentedEmptyConstructor` on the no-arg constructors JPA needs. Fixed with
  a comment in each, not suppressed.
- Hand mutation testing:
  - Skipping the "stored staff matches" check: killed by
    `assignmentWhoseStaffDiffersFromStoredRecordIsRejected`.
  - Removing `@OrderColumn`: **survived**, even after adding
    `resavingInADifferentOrderKeepsTheNewOrder`. Hibernate deletes and re-inserts the
    whole collection on save, and H2 returns rows in insertion order. Kept the column
    and the test; recorded as gap 18 (needs a Postgres run).
- `./gradlew clean build`: 57 tests (20 new), 0 failures, 0 PMD, 0 SpotBugs.
- Not checked: Postgres. `prod` uses `ddl-auto=validate` with no migration tool (gap 17).

## Outcome

- **AI-written:** all of `repo` except the original `package-info.java` text, the four
  test classes and `RepoFixtures`, GAPS rows 17–18, this log, commit subject.
- **Human-written:** <!-- TODO: Tushar -->
- **Verification:** as listed above. Human line-by-line review still to do before the PR.
- **Rejected:** public entities or Spring Data interfaces (would let JPA types leak to
  `service`); a separate shift-type table (`domain` has none); one `AssignmentEntity`
  with its own id (an element collection is enough, since assignments have no identity
  outside their roster).
