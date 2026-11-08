package com.iitm.roster.scheduler;

import com.iitm.roster.constraints.WeeklyOffConstraint;
import com.iitm.roster.domain.DayOff;
import com.iitm.roster.domain.Leave;
import com.iitm.roster.domain.Roster;
import com.iitm.roster.domain.Staff;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Weekly offs doctors are owed. A doctor who gets no off in a week that counts (see
 * {@link WeeklyOffConstraint#countedWeeks}), and is not on leave for all of it, is owed one, and
 * the next month gives it as an extra off. An owed off the month could not give stays owed.
 */
public final class OwedOffs {

	private OwedOffs() {
	}

	/**
	 * What is owed once {@code roster}'s month is over.
	 *
	 * @param doctors the doctors on the month's list; anyone else's owed offs are dropped
	 * @param owedBefore offs owed going into the month; a doctor owed two appears twice
	 * @return the offs owed afterwards, in {@code doctors}' order; a doctor owed two appears twice
	 */
	public static List<Staff> after(Roster roster, List<Staff> doctors, List<Leave> leaves, List<Staff> owedBefore) {
		List<List<LocalDate>> weeks = WeeklyOffConstraint.countedWeeks(roster);
		List<Staff> owed = new ArrayList<>();
		for (Staff doctor : doctors) {
			Set<LocalDate> offs = new HashSet<>();
			for (DayOff off : roster.weeklyOffs()) {
				if (off.staff().id().equals(doctor.id())) {
					offs.add(off.date());
				}
			}
			int weeksWithAnOff = 0;
			int missed = 0;
			for (List<LocalDate> week : weeks) {
				if (week.stream().anyMatch(offs::contains)) {
					weeksWithAnOff++;
				} else if (!onLeaveAllOf(doctor, week, leaves)) {
					missed++;
				}
			}
			int extras = offs.size() - weeksWithAnOff;
			long before = owedBefore.stream().filter(s -> s.id().equals(doctor.id())).count();
			long total = Math.max(0, before - extras) + missed;
			for (long i = 0; i < total; i++) {
				owed.add(doctor);
			}
		}
		return owed;
	}

	private static boolean onLeaveAllOf(Staff doctor, List<LocalDate> days, List<Leave> leaves) {
		return days.stream().allMatch(day -> leaves.stream()
				.anyMatch(l -> l.staff().id().equals(doctor.id()) && l.covers(day)));
	}

}
