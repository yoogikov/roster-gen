"""Picks edits and swaps out of a roster JSON file. Synthetic doctors only."""

import json
import sys
from datetime import date, timedelta

ROSTER = sys.argv[2] if len(sys.argv) > 2 else "/tmp/roster-2026-11.json"
TODAY = date(2026, 11, 4)


def load():
    with open(ROSTER, encoding="utf-8") as handle:
        return json.load(handle)


def double_book():
    roster = load()
    by_day = {}
    for assignment in roster["assignments"]:
        by_day.setdefault(assignment["slot"]["date"], []).append(assignment)
    for day, rows in sorted(by_day.items()):
        codes = {row["slot"]["shift"]["code"]: row for row in rows}
        if "I" in codes and "II" in codes and codes["I"]["staff"]["id"] != codes["II"]["staff"]["id"]:
            print(json.dumps({
                "type": "assign",
                "date": day,
                "shift": "II",
                "staffId": codes["I"]["staff"]["id"],
                "post": "GENERAL",
            }))
            return
    raise SystemExit("no day with both day shifts")


def valid_edit():
    roster = load()
    offs = {(off["staff"]["id"], off["date"]) for off in roster["weeklyOffs"]}
    busy = {}
    nights = set()
    for assignment in roster["assignments"]:
        busy.setdefault(assignment["slot"]["date"], set()).add(assignment["staff"]["id"])
        if assignment["slot"]["shift"]["code"] == "N":
            nights.add((assignment["staff"]["id"], assignment["slot"]["date"]))
    doctors = [f"D{n:02d}" for n in range(1, 12)]
    for assignment in roster["assignments"]:
        if assignment["slot"]["shift"]["code"] != "II" or assignment["post"] != "GENERAL":
            continue
        day = date.fromisoformat(assignment["slot"]["date"])
        if day <= TODAY:
            continue
        yesterday = (day - timedelta(days=1)).isoformat()
        for doctor in doctors:
            if doctor in busy.get(day.isoformat(), ()):
                continue
            if (doctor, day.isoformat()) in offs or (doctor, yesterday) in nights:
                continue
            print(json.dumps({
                "type": "assign",
                "date": day.isoformat(),
                "shift": "II",
                "staffId": doctor,
                "post": "GENERAL",
            }))
            return
    raise SystemExit("no free doctor to assign")


def swap_pair():
    """A future day-shift exchange staff1 (D01) can offer and staff2 (D06) can accept.

    Shift I ward stays with that day's night, so it is not moved on its own. The
    receiver must be free of Shift I and Shift II that day, not on a weekly off,
    and not coming off a night (the next day is a post-duty off).
    """
    roster = load()
    offs = {(off["staff"]["id"], off["date"]) for off in roster["weeklyOffs"]}
    nights = {
        (a["staff"]["id"], a["slot"]["date"])
        for a in roster["assignments"]
        if a["slot"]["shift"]["code"] == "N"
    }
    occupied = {
        (a["staff"]["id"], a["slot"]["date"])
        for a in roster["assignments"]
        if a["slot"]["shift"]["code"] in ("I", "II")
    }

    def movable(assignment):
        code = assignment["slot"]["shift"]["code"]
        if code not in ("I", "II") or date.fromisoformat(assignment["slot"]["date"]) <= TODAY:
            return False
        return not (code == "I" and assignment["post"] == "WARD")

    def safe(left, right):
        if left["slot"]["shift"]["code"] != right["slot"]["shift"]["code"]:
            return False
        if left["slot"]["date"] == right["slot"]["date"]:
            return False
        pairs = (
            (right["staff"]["id"], left["slot"]["date"]),
            (left["staff"]["id"], right["slot"]["date"]),
        )
        for person, day in pairs:
            if (person, day) in offs or (person, day) in occupied:
                return False
            day_before = (date.fromisoformat(day) - timedelta(days=1)).isoformat()
            if (person, day_before) in nights:
                return False
        return True

    lefts = [a for a in roster["assignments"] if a["staff"]["id"] == "D01" and movable(a)]
    rights = [a for a in roster["assignments"] if a["staff"]["id"] == "D06" and movable(a)]
    for left in lefts:
        for right in rights:
            if safe(left, right):
                code = left["slot"]["shift"]["code"]
                print(json.dumps({
                    "offeredDate": left["slot"]["date"],
                    "offeredShift": code,
                    "offeredStaffId": "D01",
                    "offeredPost": left["post"],
                    "wantedDate": right["slot"]["date"],
                    "wantedShift": code,
                    "wantedStaffId": "D06",
                    "wantedPost": right["post"],
                }))
                return
    raise SystemExit("D01 and D06 have no safe day-shift pair")


def lapse_pair():
    roster = load()
    today = [a for a in roster["assignments"] if a["slot"]["date"] == TODAY.isoformat() and a["slot"]["shift"]["code"] == "I"]
    if len(today) < 2:
        raise SystemExit("November 4 has fewer than two Shift I assignments")
    by_id = {a["staff"]["id"]: a for a in today}
    left = by_id.get("D01", today[0])
    right = by_id.get("D02", today[1])
    if left["staff"]["id"] == right["staff"]["id"]:
        right = today[1]
    print(json.dumps({
        "offeredDate": TODAY.isoformat(),
        "offeredShift": "I",
        "offeredStaffId": left["staff"]["id"],
        "offeredPost": left["post"],
        "wantedDate": TODAY.isoformat(),
        "wantedShift": "I",
        "wantedStaffId": right["staff"]["id"],
        "wantedPost": right["post"],
    }))


def staff_edit():
    roster = load()
    for assignment in roster["assignments"]:
        if assignment["staff"]["id"] == "D02" and assignment["slot"]["date"] > TODAY.isoformat():
            print(json.dumps({
                "type": "unassign",
                "date": assignment["slot"]["date"],
                "shift": assignment["slot"]["shift"]["code"],
                "staffId": "D02",
            }))
            return
    raise SystemExit("no future D02 shift")


COMMANDS = {
    "double-book": double_book,
    "valid-edit": valid_edit,
    "swap": swap_pair,
    "lapse": lapse_pair,
    "staff-edit": staff_edit,
}

if __name__ == "__main__":
    COMMANDS[sys.argv[1]]()
