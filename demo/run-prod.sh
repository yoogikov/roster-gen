#!/usr/bin/env bash
# Local Postgres deploy. There is no IITM CC host (gap 29).
# Set ROSTER_ADMIN_PASSWORD first. Docker must be running.
set -euo pipefail
: "${ROSTER_ADMIN_PASSWORD:?Set ROSTER_ADMIN_PASSWORD for the four synthetic logins}"
HERE=$(cd "$(dirname "$0")/.." && pwd)
cd "$HERE"
docker compose up -d
export ROSTER_DB_URL="${ROSTER_DB_URL:-jdbc:postgresql://localhost:5432/roster}"
export ROSTER_DB_USER="${ROSTER_DB_USER:-roster}"
export ROSTER_DB_PASSWORD="${ROSTER_DB_PASSWORD:-roster}"
./gradlew bootJar
SPRING_PROFILES_ACTIVE=prod java -jar build/libs/roster-0.0.1-SNAPSHOT.jar
