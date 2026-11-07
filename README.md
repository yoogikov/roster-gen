# roster-gen: IITM Hospital duty roster

CS5013 project. A Spring Boot web app that generates, edits and publishes the monthly
duty roster for doctors at IITM Hospital, with an audit trail, leave and swap
requests, role-based authorization and CSV export.

Stakeholder: Dr. Rebecca Punithavalli, CMO, IITM Hospital.

## Team and module owners

| Member | Roll no. | GitHub | Modules |
|---|---|---|---|
| Yoogi Kovendhan | CS23B068 | [`yoogi_kov`](https://github.com/yoogi_kov) | `domain`, `constraints`, `rules`, `scheduler`, `export` |
| Tushar Jain | CS23B064 | [`tushar3q34`](https://github.com/tushar3q34) | `repo`, `service`, `requests`, `security`, `web` |

Package responsibilities and allowed dependencies are documented in each
`src/main/java/com/iitm/roster/*/package-info.java`. A layer may only call the layer
directly below it:

```
web                                  (presentation)
service   requests   security        (application)
scheduler rules      repo    export  (planning & persistence)
constraints                          (policy)
domain                               (core, depends on nothing)
```

`requests` also calls `service` and `security` (same layer) so a swap approval can
run `applyEdit` and `AuthorizationPolicy`. That is gap 27.

## Install

1. JDK 21 (Temurin 21 is what CI uses). Confirm with `java -version`.
2. Clone this repository. Gradle does not need to be installed; use `./gradlew`.
3. Docker is needed only for the `prod` profile (Postgres 16).

```bash
git clone https://github.com/yoogikov/roster-gen.git
cd roster-gen
./gradlew test
```

Use synthetic doctors only (`Test Doctor 1` … `Test Doctor 11`, ids `D01` … `D11`).
There is no import of a real hospital sheet.

## Run (dev, default)

```bash
export ROSTER_DEV_PASSWORD='<pick-one>'
./gradlew bootRun
```

`dev` uses in-memory H2, recreates the schema on every start, and serves the H2
console at `http://localhost:8080/h2-console` (JDBC URL `jdbc:h2:mem:roster`,
user `sa`, empty password). The console still asks for HTTP Basic, same as the
app.

Open `http://localhost:8080`. The browser prompts for HTTP Basic. There is no
session cookie and CSRF is off.

Pages:

| Path | What it is |
|---|---|
| `/` | Home: generate a month (admin), with optional leave lines (`id,first,last`) and holidays (blank keeps the saved ones, `none` clears) |
| `/rosters/2026-12` | Roster sheet. Staff see it read-only |
| `/audit` | Audit log |
| `/inbox` | Swap, change and leave requests |
| `/rules` | Rule templates |
| `/leave` | Leave request form |

CSV and PDF downloads are on the sheet and at
`GET /api/rosters/{month}/export?format=csv` (or `format=pdf`). The PDF is A4 landscape,
one row per day, with the same columns as the CSV and weekends shaded.

`demo/mid-demo.sh` drives the JSON API: generate, read, export CSV, reject a
double-booking, accept one edit, publish, swap, lapse, and show a staff edit of
a draft refused. There is no import endpoint.

```bash
ROSTER_DEV_PASSWORD='<pick-one>' ./gradlew bootRun
# another terminal
./demo/mid-demo.sh
```

## Admin account

Every request needs HTTP Basic. Logins are stored in the `user_account` table.
On start, if no admin exists, one is created from `ROSTER_ADMIN_PASSWORD` (prod) or
`ROSTER_DEV_PASSWORD` (dev). Prod refuses to start without `ROSTER_ADMIN_PASSWORD`.

`dev` also seeds reception, doctors D01/D06, and staff1/staff2 for the demo:

| Username | Role | Staff id | Password |
|---|---|---|---|
| `admin` | ADMIN | — | `ROSTER_DEV_PASSWORD` / `ROSTER_ADMIN_PASSWORD` |
| `reception` | RECEPTION | — | same (dev seed only) |
| `staff1` | STAFF | `D01` | same (dev seed only) |
| `staff2` | STAFF | `D06` | same (dev seed only) |

If `ROSTER_DEV_PASSWORD` is unset, `dev` generates a random password and prints it
in the startup log.

Admin manages doctors and logins on `/doctors` (add, rename, deactivate, create
login with a one-time temporary password, reset password). Anyone signed in can
change their own password on `/password`. Staff can request leave for themselves
before any month is generated. Reception can view.

## Run (prod, local Postgres)

There is no IITM CC account and no consent for a public host (gap 29). Run
Postgres on the machine that will serve the app. Flyway applies
`V1__hospital_roster.sql` and `V2__user_accounts.sql` on the first start;
Hibernate only validates after that.

```bash
export ROSTER_ADMIN_PASSWORD='<pick-one>'
./demo/run-prod.sh
```

Or by hand:

```bash
docker compose up -d
export ROSTER_DB_URL=jdbc:postgresql://localhost:5432/roster
export ROSTER_DB_USER=roster
export ROSTER_DB_PASSWORD=roster
export ROSTER_ADMIN_PASSWORD='<pick-one>'
./gradlew bootJar
SPRING_PROFILES_ACTIVE=prod java -jar build/libs/roster-0.0.1-SNAPSHOT.jar
```

If any of `ROSTER_DB_URL`, `ROSTER_DB_USER`, `ROSTER_DB_PASSWORD` or
`ROSTER_ADMIN_PASSWORD` is missing, the app refuses to start.

The four synthetic logins are the ones in [Admin account](#admin-account). Log in
as `admin` with `ROSTER_ADMIN_PASSWORD`.

## Generate a roster from a doctor list

No app, login or database needed. The input is a CSV of doctors in night-rotation order,
with their leave (see `src/test/resources/fixtures/doctors-october-2026.csv`):

```
./gradlew generate --args="src/test/resources/fixtures/doctors-october-2026.csv 2026-10 --holidays 2026-10-02,2026-10-20 --out roster.csv"
```

`roster.csv` gets one row per day: nights, ward, casualty and general duty on each day
shift, post-duty off and weekly offs. A summary is printed: whether every shift was filled,
any shifts left unfilled, and which rules were broken. Without `--out`, the CSV goes to
standard output. Exit code 0 = all filled, 1 = some unfilled (roster still written),
2 = bad input.

## Test and build

```bash
./gradlew test     # JUnit 5 + AssertJ + jqwik
./gradlew build    # compile, test, PMD, SpotBugs, jar
```

`RosterJourneyTest` is the full path the plan asked for: save a rule, generate
December, edit, swap, export CSV, then check the audit sequence.

Reports are written to `build/reports/` (`tests/`, `pmd/`, `spotbugs/`). PMD and
SpotBugs are report-only (`ignoreFailures`): findings do not fail the build, but
each one must be fixed or suppressed with a comment saying why. `web`, `service`
and `repo` currently have none.

CI (`.github/workflows/build.yml`) runs `./gradlew build` on every push and pull
request.

## Documentation

- `docs/ai-log/`: Tushar's later task logs (index in `docs/ai-log/README.md`). Older logs
  and the per-stage disclosures are kept outside the repo until submission. Commits with
  AI help carry a `Co-authored-by` trailer.
- `docs/GAPS.md`: honest list of what is missing or behind plan
- `docs/weekly-log/`: weekly contribution paragraph per member

## Contact

- Yoogi Kovendhan (CS23B068): yoogikov@gmail.com · GitHub [`yoogi_kov`](https://github.com/yoogi_kov)
- Tushar Jain (CS23B064): tushar3q34@gmail.com · GitHub [`tushar3q34`](https://github.com/tushar3q34)
