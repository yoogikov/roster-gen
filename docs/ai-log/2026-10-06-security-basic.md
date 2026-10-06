# HTTP Basic security and dev users

| Field | Value |
|---|---|
| Owner | Tushar |
| Module(s) | `security` |
| Branch / commits | `feat/security-basic` / "Add HTTP Basic security config with synthetic dev users" |
| Tool and model | Cursor agent, Claude Opus 5.5 |
| Date | 2026-10-06 |

## Prompt v1

```text
Make next commit of Tushar i.e. me using this plan. Keep commit message on the same fashion as this one
```

Attached: `PROJECT-PLAN.md` (plan for 3 Oct – 6 Nov). The agent took Tushar's first
Phase B task, `feat/security-basic`: "write `AuthorizationPolicy` and `Decision` by hand.
Spring Security config with dev-only in-memory synthetic users for each role (HTTP Basic
for the API). First `AuthorizationPolicyTest` cases."

## Response v1 (summary)

- `SecurityConfig`: every request authenticated, HTTP Basic, stateless sessions,
  delegating (bcrypt) `PasswordEncoder`.
- `DevUsersConfig` (`@Profile("dev")`): synthetic users `admin`, `reception`, `staff1`,
  `staff2` sharing one password from `ROSTER_DEV_PASSWORD`; random and logged if unset.
- `SecurityConfigTest` (7, MockMvc under `dev`) and `DevUsersConfigTest` (4, plain JUnit).
- README auth section; two new rows in `docs/GAPS.md`.

**Deliberately not done:** `AuthorizationPolicy`, `Decision` and `AuthorizationPolicyTest`.
The plan requires the interfaces to be hand-written, and they need `domain` types
(`Actor`, `Action`, `Roster`) that do not exist yet. Recorded as gap 9.

## What was wrong / what was checked

- Spring Boot 4.1.1 / Spring Security 7.1.1 class locations (`AutoConfigureMockMvc` in
  `org.springframework.boot.webmvc.test.autoconfigure`, `SecurityMockMvcRequestPostProcessors`,
  `SessionCreationPolicy`, `InMemoryUserDetailsManager`) checked in the resolved jars
  before use, not taken from model memory.
- Hand mutation testing of `SecurityConfig`:
  - `anyRequest().permitAll()`: killed by `requestWithoutCredentialsIsChallengedForBasicAuth`.
  - `SessionCreationPolicy.ALWAYS`: **survived** the first version of
    `authenticatedRequestDoesNotCreateSession`, which checked for a `Set-Cookie` header
    (MockMvc never writes one). Changed the test to assert no `HttpSession` exists; the
    mutant is now killed.
- `./gradlew clean build`: BUILD SUCCESSFUL, 13 tests, 0 failures, 0 PMD, 0 SpotBugs.
- Manual run of the jar with `ROSTER_DEV_PASSWORD=demo-pw` and curl on `/api/ping`:
  no credentials gives 401, `admin:demo-pw` gives 404 (authenticated, no endpoint yet),
  wrong password gives 401.
- CSRF is left at Spring's default (on). Not needed for the GET-only tests; to be
  decided with the REST API, where curl sends POSTs.
- The fallback logs a generated password at WARN. Dev profile only, same behaviour as
  Spring Boot's default user; no fixed credential is in the code or config.

## Outcome

- **AI-written:** `SecurityConfig`, `DevUsersConfig`, both test classes, the
  `application-dev.properties` line, README and GAPS edits, this log, commit subject.
- **Human-written:** <!-- TODO: Tushar --> `AuthorizationPolicy` and `Decision` will be.
- **Verification:** as listed above. Human line-by-line review still to do before the PR.
- **Rejected:** hardcoding dev passwords in code or properties (conflicts with "no
  hardcoded credentials"); URL-pattern role rules (authorization belongs in
  `AuthorizationPolicy`).
