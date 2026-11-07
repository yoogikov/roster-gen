# Dockerfile and local Docker deploy

| Field | Value |
|---|---|
| Owner | Tushar |
| Module(s) | deploy / docs |
| Branch / commits | `feat/docker-deploy` |
| Tool and model | Cursor agent, Composer |
| Date | 2026-11-07 |

## Prompt v1

```text
Complete the entire plan (parts of Tushar)
(roster-gen_what_Tushar_needs_to_do_next: Task 5 Dockerfile and local deployment)
```

## Response v1 (summary)

- Multi-stage Dockerfile: JDK 21 build `bootJar`, JRE 21 run as non-root `roster`.
- Default image uses `dev` (H2); unset password prints a generated one in the log.
- `docker-compose.yml` builds the app with Postgres, Flyway, named volume; admin password from `.env`.
- README Build-and-run box updated for both setups; `.env.example` and `.dockerignore`.

## What was wrong / what was checked

- `docker build` / `docker run` (run before push when Docker is available).
- No secrets in the image layers; `.env` stays git-ignored.

## Outcome

- **AI-written:** Dockerfile, compose app service, README Docker section, this log.
- **Human-written:** plan Done-when criteria.
- **Verification:** image builds; compose brings up app+Postgres.
- **Rejected:** baking a password into the image.
