package com.iitm.roster.scheduler;

import com.iitm.roster.constraints.StaffingPlan;
import com.iitm.roster.constraints.StaffingRequirement;
import com.iitm.roster.constraints.WeeklyOffConstraint;
import com.iitm.roster.domain.DayOff;
import com.iitm.roster.domain.Leave;
import com.iitm.roster.domain.Roster;
import com.iitm.roster.domain.ShiftType;
import com.iitm.roster.domain.Staff;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Gives each doctor one weekly off in every week that counts (see {@link WeeklyOffConstraint}).
 *
 * <p>Preferred days, in order: Saturday or Sunday (whichever has more doctors to spare),
 * then Friday, then Monday, then any other day of the week. A day is only used if the
 * doctor is not on leave, not on nights and not post-duty off that day, and enough doctors
 * are left to staff that day's day shifts.
 */
public final class WeeklyOffPlanner {

	private static final List<DayOfWeek> AFTER_WEEKEND = List.of(DayOfWeek.FRIDAY, DayOfWeek.MONDAY);

	private final StaffingPlan staffing;
	private final List<ShiftType> dayShifts;

	public WeeklyOffPlanner(StaffingPlan staffing, List<ShiftType> dayShifts) {
		this.staffing = Objects.requireNonNull(staffing, "staffing");
		this.dayShifts = List.copyOf(dayShifts);
	}

	public List<DayOff> plan(YearMonth month, List<Staff> doctors, Map<LocalDate, Staff> nights, List<Leave> leaves) {
		List<DayOff> offs = new ArrayList<>();
		for (List<LocalDate> week : WeeklyOffConstraint.countedWeeks(Roster.empty(month))) {
			for (Staff doctor : doctors) {
				if (week.stream().allMatch(day -> onLeave(doctor, day, leaves))) {
					continue;
				}
				for (LocalDate day : preferenceOrder(week, doctors, nights, leaves, offs)) {
					if (canTakeOff(doctor, day, doctors, nights, leaves, offs)) {
						offs.add(new DayOff(doctor, day));
						break;
					}
				}
			}
		}
		return offs;
	}

	private List<LocalDate> preferenceOrder(List<LocalDate> week, List<Staff> doctors, Map<LocalDate, Staff> nights,
			List<Leave> leaves, List<DayOff> offs) {
		List<LocalDate> weekend = new ArrayList<>(week.stream()
				.filter(d -> d.getDayOfWeek() == DayOfWeek.SATURDAY || d.getDayOfWeek() == DayOfWeek.SUNDAY)
				.toList());
		weekend.sort(Comparator.comparingInt((LocalDate d) -> -spare(d, doctors, nights, leaves, offs)));
		List<LocalDate> order = new ArrayList<>(weekend);
		for (DayOfWeek fallback : AFTER_WEEKEND) {
			week.stream().filter(d -> d.getDayOfWeek() == fallback).forEach(order::add);
		}
		week.stream().filter(d -> !order.contains(d)).forEach(order::add);
		return order;
	}

	private boolean canTakeOff(Staff doctor, LocalDate day, List<Staff> doctors, Map<LocalDate, Staff> nights,
			List<Leave> leaves, List<DayOff> offs) {
		boolean busy = onLeave(doctor, day, leaves) || doctor.equals(nights.get(day))
				|| doctor.equals(nights.get(day.minusDays(1)));
		return !busy && spare(day, doctors, nights, leaves, offs) > 0;
	}

	/** Doctors free to work {@code day} beyond what its day shifts need. */
	private int spare(LocalDate day, List<Staff> doctors, Map<LocalDate, Staff> nights, List<Leave> leaves, List<DayOff> offs) {
		long free = doctors.stream()
				.filter(d -> !onLeave(d, day, leaves))
				.filter(d -> !d.equals(nights.get(day.minusDays(1))))
				.filter(d -> !offs.contains(new DayOff(d, day)))
				.count();
		int needed = 0;
		for (StaffingRequirement requirement : staffing.on(day)) {
			if (dayShifts.contains(requirement.shift())) {
				needed += requirement.minimum();
			}
		}
		return (int) free - needed;
	}

	private static boolean onLeave(Staff doctor, LocalDate day, List<Leave> leaves) {
		return leaves.stream().anyMatch(l -> l.staff().equals(doctor) && l.covers(day));
	}

}
