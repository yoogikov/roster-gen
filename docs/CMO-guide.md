# Using the duty roster

This guide is for the person who runs the roster each month. Sign in at the address you were given, with the `admin` username and password.

## First time: add your doctors

Open **Doctors** and add each doctor with a short id, such as D01, and their full name. Give the ids in night-rotation order, because the home page lists doctors by id and nights follow that list.

To let a doctor sign in, press **Create login** beside their name. The page shows a temporary password once, so pass it on straight away. They can change it from **Password** after signing in.

## Each month

1. **Open the home page.** The next month and your active doctors are already filled in. Remove anyone who is not working that month.
2. **Enter leave and holidays if you know them.** Leave is one line per doctor: id, first day, last day, for example `D03,2026-12-10,2026-12-12`. Holidays are dates separated by commas, and they are staffed like weekends.
3. **Leave "First on nights" blank** to carry on the rotation from last month. For your very first month, type the id of the doctor who starts.
4. **Press Generate.** The message at the top says if any shifts could not be filled, and which rules are broken.
5. **Open the month's sheet and check it.** Its address is `/rosters/` followed by the month, for example `/rosters/2026-12`.
6. **Make any changes.** Use **Edit a shift** to assign, remove or move a doctor, and **Move a weekly off** to change an off day. A change that breaks a rule is refused and the message says why.
7. **Press Publish** when it is ready. Doctors can then see it. A published month cannot be generated again, only changed shift by shift.

Use **Download CSV** or **Download PDF** on the sheet to save or print it.

## Weekly offs that are owed

Sometimes a week has no room for everyone's weekly off, most often the last few days of a month. Those doctors are listed at the top of the sheet as owed. Next month's roster gives them an extra off on the first day with room, so generate the months in order.

## Leave and swap requests

Requests appear in the **Inbox**.

- **Leave:** approve or reject it. If you approve leave on a draft that nobody has changed, the month is rebuilt with it. If you have changed the draft, or it is published, your changes stay and the doctor is simply taken off those days. Any shifts this empties are listed for you to fill.
- **Cancelling leave:** approved leave appears under **Approved leave** with a **Cancel leave** button. If the month is published or you have changed it, the doctor is not put back on shifts automatically.
- **Swaps:** a doctor proposes a swap on the published sheet, the other doctor accepts, and you approve. A swap is refused if it would break a rule. If it is not approved before the shift starts, it lapses and nothing changes.

## If something looks wrong

| What you see | What to do |
|---|---|
| Shifts could not be filled | Too few doctors are free that week. Fill them by hand, or lower the doctors per shift and generate again. |
| "no roster for ... to continue the nights from" | Last month has not been generated. Type who does the first night. |
| A change is refused | The message names the rule. Choose another doctor or day. |
| A doctor's name has a question mark in the PDF | The PDF only has Latin letters. Write the name in English letters. |

**Audit** lists every change, who made it and when.
