# roster-gen: IITM Hospital duty roster

CS5013 project. A Spring Boot web app that generates, edits and publishes the monthly
duty roster for doctors and nurses at IITM Hospital, with an audit trail, leave and swap
requests, role-based authorization and PDF export.

Stakeholder: Dr. Rebecca Punithavalli, CMO, IITM Hospital.

## Team and module owners

| Member | Roll no. | GitHub | Modules (proposed, not yet confirmed) |
|---|---|---|---|
| Yoogi Kovendhan | CS23B068 | `yoogi_kov` | `domain`, `constraints`, `rules`, `scheduler`, `export` |
| Tushar Jain | CS23B064 | `tushar3q34` | `repo`, `service`, `requests`, `security`, `web` |

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

## Prerequisites

- JDK 21 (e.g. Temurin 21). Gradle does not need to be installed; use the wrapper.
- Postgres only for the `prod` profile.

## Run (dev profile)

```bash
./gradlew bootRun
```

`dev` is the default profile: in-memory H2, schema recreated on every start, H2 console
at `http://localhost:8080/h2-console` (JDBC URL `jdbc:h2:mem:roster`, user `sa`, empty
password). Use synthetic data only.

Every request needs HTTP Basic auth; no session is kept. The `dev` profile has synthetic
users `admin` (ADMIN), `reception` (RECEPTION), `staff1` and `staff2` (STAFF), all with
the password in `ROSTER_DEV_PASSWORD`. If that is unset, a random password is generated
and printed in the startup log.

```bash
ROSTER_DEV_PASSWORD=<pick-one> ./gradlew bootRun
curl -u admin:<pick-one> http://localhost:8080/api/...
```

The H2 console is not yet exempted from the security config.

## Test and build

```bash
./gradlew test     # JUnit 5 + AssertJ + jqwik
./gradlew build    # compile, test, PMD, SpotBugs, jar
```

Reports are written to `build/reports/` (`tests/`, `pmd/`, `spotbugs/`). PMD and
SpotBugs are report-only for now: findings do not fail the build, but each one must be
fixed or suppressed with `@SuppressWarnings` plus a comment saying why.

CI (`.github/workflows/build.yml`) runs `./gradlew build` on every push and pull request.

## Run (prod profile)

Credentials are read only from environment variables:

```bash
export ROSTER_DB_URL=jdbc:postgresql://<host>:5432/<db>
export ROSTER_DB_USER=<user>
export ROSTER_DB_PASSWORD=<password>
SPRING_PROFILES_ACTIVE=prod java -jar build/libs/roster-0.0.1-SNAPSHOT.jar
```

If any variable is missing the app refuses to start. Hibernate only validates the
schema in `prod`; it never changes it.

## Documentation

- `docs/ai-log/TEMPLATE.md`: the template for each AI-assisted task's log. The logs and the
  per-stage AI-use disclosures are kept outside the repo for now and added at the final
  submission. Commits with AI help carry a `Co-Authored-By` trailer.
- `docs/GAPS.md`: honest list of what is missing or behind plan
- `docs/weekly-log/`: weekly contribution paragraph per member

## Contact

- Yoogi Kovendhan: <!-- TODO: email -->
- Tushar Jain: <!-- TODO: email -->
