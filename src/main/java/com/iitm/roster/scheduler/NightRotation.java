package com.iitm.roster.scheduler;

import com.iitm.roster.domain.Leave;
import com.iitm.roster.domain.Staff;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Plans who does nights, following the CMO's fixed rotation.
 *
 * <ul>
 *   <li>Doctors take nights in a fixed cyclic order, one per day.
 *   <li>Short leave (at most {@value #LONG_LEAVE_DAYS} days): if it is someone's turn while
 *       they are on leave, they swap places in the order with the next doctor who can do it,
 *       and the order otherwise carries on.
 *   <li>Long leave (more than {@value #LONG_LEAVE_DAYS} days): the doctor is taken out of the
 *       order from the first day of leave, and put back in on the day they return, doing that
 *       night themselves.
 * </ul>
 *
 * <p>A day nobody can take is left out of the result; the generator then reports it.
 */
public final class NightRotation {

	/** Leave longer than this takes a doctor out of the rotation. */
	public static final int LONG_LEAVE_DAYS = 10;

	private NightRotation() {
	}

	/**
	 * @param order the rotation order
	 * @param firstOnNights who does the first night of the month; must be in {@code order}
	 * @return the night doctor for each day that could be filled, in date order
	 */
	public static Map<LocalDate, Staff> plan(YearMonth month, List<Staff> order, Staff firstOnNights, List<Leave> leaves) {
		int start = order.indexOf(Objects.requireNonNull(firstOnNights, "firstOnNights"));
		if (start < 0) {
			throw new IllegalArgumentException(firstOnNights.name() + " is not in the night rotation");
		}
		List<Staff> cycle = new ArrayList<>(order.subList(start, order.size()));
		cycle.addAll(order.subList(0, start));

		Map<LocalDate, Staff> nights = new LinkedHashMap<>();
		List<Staff> outOnLongLeave = new ArrayList<>();
		int position = 0;
		for (LocalDate day = month.atDay(1); !day.isAfter(month.atEndOfMonth()); day = day.plusDays(1)) {
			position = takeOutLongLeave(cycle, outOnLongLeave, day, leaves, position);
			Staff returning = returningOn(outOnLongLeave, day, leaves);
			if (returning != null) {
				outOnLongLeave.remove(returning);
				cycle.add(position, returning);
			}
			if (cycle.isEmpty()) {
				continue;
			}
			position %= cycle.size();
			int available = nextAvailable(cycle, position, day, leaves, nights);
			if (available < 0) {
				continue;
			}
			swap(cycle, position, available);
			nights.put(day, cycle.get(position));
			position = (position + 1) % cycle.size();
		}
		return nights;
	}

	/** Removes everyone whose long leave covers {@code day}; returns the adjusted position. */
	private static int takeOutLongLeave(List<Staff> cycle, List<Staff> out, LocalDate day, List<Leave> leaves, int position) {
		int adjusted = position;
		for (int i = cycle.size() - 1; i >= 0; i--) {
			Staff doctor = cycle.get(i);
			if (onLongLeave(doctor, day, leaves)) {
				cycle.remove(i);
				out.add(doctor);
				if (i < adjusted) {
					adjusted--;
				}
			}
		}
		return adjusted;
	}

	private static Staff returningOn(List<Staff> out, LocalDate day, List<Leave> leaves) {
		for (Staff doctor : out) {
			boolean leaveEndedYesterday = leaves.stream().anyMatch(l -> l.staff().equals(doctor)
					&& isLong(l) && l.lastDay().plusDays(1).equals(day));
			if (leaveEndedYesterday && !onAnyLeave(doctor, day, leaves)) {
				return doctor;
			}
		}
		return null;
	}

	/** Index of the first doctor from {@code position} on (wrapping) who can do tonight, or -1. */
	private static int nextAvailable(List<Staff> cycle, int position, LocalDate day, List<Leave> leaves, Map<LocalDate, Staff> nights) {
		for (int step = 0; step < cycle.size(); step++) {
			int index = (position + step) % cycle.size();
			Staff doctor = cycle.get(index);
			boolean onNightsYesterday = doctor.equals(nights.get(day.minusDays(1)));
			if (!onAnyLeave(doctor, day, leaves) && !onNightsYesterday) {
				return index;
			}
		}
		return -1;
	}

	private static void swap(List<Staff> cycle, int a, int b) {
		Staff first = cycle.get(a);
		cycle.set(a, cycle.get(b));
		cycle.set(b, first);
	}

	private static boolean isLong(Leave leave) {
		return ChronoUnit.DAYS.between(leave.firstDay(), leave.lastDay()) + 1 > LONG_LEAVE_DAYS;
	}

	private static boolean onLongLeave(Staff doctor, LocalDate day, List<Leave> leaves) {
		return leaves.stream().anyMatch(l -> l.staff().equals(doctor) && isLong(l) && l.covers(day));
	}

	private static boolean onAnyLeave(Staff doctor, LocalDate day, List<Leave> leaves) {
		return leaves.stream().anyMatch(l -> l.staff().equals(doctor) && l.covers(day));
	}

}
