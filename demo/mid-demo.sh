#!/usr/bin/env bash
# Mid-demo flow against a running app (./gradlew bootRun, dev profile).
# There is no roster import: generation takes a doctor list and the hospital's staffing numbers.
# Set ROSTER_DEV_PASSWORD to the same value the app was started with.
set -euo pipefail

BASE="${ROSTER_BASE:-http://localhost:8080}"
: "${ROSTER_DEV_PASSWORD:?Set ROSTER_DEV_PASSWORD to the dev user password}"
HERE=$(cd "$(dirname "$0")" && pwd)

curl_as() {
	local user="$1"
	shift
	curl -sS -u "$user:$ROSTER_DEV_PASSWORD" "$@"
}

count_audit() {
	curl_as admin "$BASE/api/audit" | python3 -c 'import json,sys; print(len(json.load(sys.stdin)))'
}

echo "1. ADMIN generates November 2026 from a doctor list. No import."
GEN=$(curl_as admin -H 'Content-Type: application/json' --data @"$HERE/generate-november.json" "$BASE/api/rosters/generate")
echo "$GEN"
printf '%s' "$GEN" | grep -q '"outcome"'

echo "2. GET the roster and download it as CSV."
curl_as admin -o /tmp/roster-2026-11.json "$BASE/api/rosters/2026-11"
curl_as admin -o /tmp/roster-2026-11.csv "$BASE/api/rosters/2026-11/export?format=csv"
head -n 3 /tmp/roster-2026-11.csv

echo "3. An edit that double-books someone is rejected and writes no audit row."
BEFORE=$(count_audit)
python3 "$HERE/steps.py" double-book /tmp/roster-2026-11.json > /tmp/double-book.json
CODE=$(curl_as admin -o /tmp/edit-bad.json -w '%{http_code}' -H 'Content-Type: application/json' \
	--data @/tmp/double-book.json "$BASE/api/rosters/2026-11/edits")
echo "double-book HTTP $CODE"
cat /tmp/edit-bad.json
echo
test "$CODE" = "422"
test "$(count_audit)" = "$BEFORE"

echo "4. A valid edit is accepted and writes one audit row."
python3 "$HERE/steps.py" valid-edit /tmp/roster-2026-11.json > /tmp/edit-ok.json
CODE=$(curl_as admin -o /tmp/edit-good.json -w '%{http_code}' -H 'Content-Type: application/json' \
	--data @/tmp/edit-ok.json "$BASE/api/rosters/2026-11/edits")
echo "valid edit HTTP $CODE"
cat /tmp/edit-good.json
echo
test "$CODE" = "200"
test "$(count_audit)" = "$((BEFORE + 1))"

echo "5. Publish, then staff1 proposes a swap, staff2 accepts, and ADMIN approves."
curl_as admin -X POST "$BASE/api/rosters/2026-11/publish" -o /tmp/published.json
curl_as admin "$BASE/api/rosters/2026-11" -o /tmp/roster-2026-11.json
python3 "$HERE/steps.py" swap /tmp/roster-2026-11.json > /tmp/swap.json
PROPOSE=$(curl_as staff1 -H 'Content-Type: application/json' --data @/tmp/swap.json "$BASE/api/swaps")
echo "propose $PROPOSE"
ID=$(printf '%s' "$PROPOSE" | python3 -c 'import json,sys; print(json.load(sys.stdin)["id"])')
echo "accept $(curl_as staff2 -X POST "$BASE/api/swaps/$ID/accept")"
APPROVE=$(curl_as admin -X POST "$BASE/api/swaps/$ID/approve")
echo "approve $APPROVE"
printf '%s' "$APPROVE" | python3 -c 'import json,sys; assert json.load(sys.stdin)["state"]=="APPROVED"'

echo "6. A swap for today is left open. The lapse job marks it LAPSED and does not move the roster."
python3 "$HERE/steps.py" lapse /tmp/roster-2026-11.json > /tmp/lapse.json
LAPSE=$(curl_as admin -H 'Content-Type: application/json' --data @/tmp/lapse.json "$BASE/api/swaps")
echo "lapse propose $LAPSE"
LID=$(printf '%s' "$LAPSE" | python3 -c 'import json,sys; print(json.load(sys.stdin)["id"])')
STATE=""
for _ in 1 2 3 4 5 6; do
	sleep 5
	STATE=$(curl_as admin "$BASE/api/swaps/$LID" | python3 -c 'import json,sys; print(json.load(sys.stdin)["state"])')
	echo "swap $LID is $STATE"
	if [ "$STATE" = "LAPSED" ]; then
		break
	fi
done
test "$STATE" = "LAPSED"

echo "7. staff1 editing another doctor's published shift is forbidden."
python3 "$HERE/steps.py" staff-edit /tmp/roster-2026-11.json > /tmp/staff-edit.json
CODE=$(curl_as staff1 -o /tmp/staff-edit-out.json -w '%{http_code}' -H 'Content-Type: application/json' \
	--data @/tmp/staff-edit.json "$BASE/api/rosters/2026-11/edits")
echo "staff edit HTTP $CODE"
cat /tmp/staff-edit-out.json
echo
test "$CODE" = "403"

echo "Done. Run ./gradlew build separately; this script talks to the running app."
