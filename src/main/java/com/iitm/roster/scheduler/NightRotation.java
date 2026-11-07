package com.iitm.roster.scheduler;

import com.iitm.roster.domain.Assignment;
import com.iitm.roster.domain.Leave;
import com.iitm.roster.domain.Roster;
import com.iitm.roster.domain.ShiftType;
import com.iitm.roster.domain.Staff;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

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
		return plan(month, order, firstOnNights, leaves, null);
	}

	/**
	 * As {@link #plan(YearMonth, List, Staff, List)}, but {@code nightBefore}, the doctor on the
	 * night before the month starts (null if none), does not get the 1st's night as well: the next
	 * doctor who can takes it, swapping places in the order as for short leave.
	 */
	public static Map<LocalDate, Staff> plan(YearMonth month, List<Staff> order, Staff firstOnNights, List<Leave> leaves,
			Staff nightBefore) {
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
			int available = nextAvailable(cycle, position, day, leaves, nights, nightBefore);
			if (available < 0) {
				continue;
			}
			swap(cycle, position, available);
			nights.put(day, cycle.get(position));
			position = (position + 1) % cycle.size();
		}
		return nights;
	}

	/**
	 * Who does the first night of the month after {@code previous}, so the rotation carries on:
	 * the doctor after the latest night in {@code previous} done by someone in {@code order},
	 * wrapping from the last doctor to the first. A doctor who has left (not in {@code order}) is
	 * skipped, so the rotation continues after the latest night by someone who stayed. Doctors
	 * are matched by id.
	 *
	 * @return empty if nobody in {@code order} did a night in {@code previous}
	 */
	public static Optional<Staff> firstAfter(Roster previous, ShiftType night, List<Staff> order) {
		List<Assignment> latestFirst = previous.assignments().stream()
				.filter(assignment -> assignment.slot().shift().equals(night))
				.sorted(Comparator.comparing((Assignment assignment) -> assignment.slot().date()).reversed())
				.toList();
		for (Assignment last : latestFirst) {
			for (int i = 0; i < order.size(); i++) {
				if (order.get(i).id().equals(last.staff().id())) {
					return Optional.of(order.get((i + 1) % order.size()));
				}
			}
		}
		return Optional.empty();
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
	private static int nextAvailable(List<Staff> cycle, int position, LocalDate day, List<Leave> leaves, Map<LocalDate, Staff> nights,
			Staff nightBefore) {
		boolean firstOfMonth = day.getDayOfMonth() == 1;
		for (int step = 0; step < cycle.size(); step++) {
			int index = (position + step) % cycle.size();
			Staff doctor = cycle.get(index);
			boolean onNightsYesterday = doctor.equals(nights.get(day.minusDays(1)))
					|| firstOfMonth && nightBefore != null && doctor.id().equals(nightBefore.id());
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

	/** More than {@value #LONG_LEAVE_DAYS} days, counting both the first and the last day. */
	static boolean isLong(Leave leave) {
		return ChronoUnit.DAYS.between(leave.firstDay(), leave.lastDay()) + 1 > LONG_LEAVE_DAYS;
	}

	private static boolean onLongLeave(Staff doctor, LocalDate day, List<Leave> leaves) {
		return leaves.stream().anyMatch(l -> l.staff().equals(doctor) && isLong(l) && l.covers(day));
	}

	private static boolean onAnyLeave(Staff doctor, LocalDate day, List<Leave> leaves) {
		return leaves.stream().anyMatch(l -> l.staff().equals(doctor) && l.covers(day));
	}

}
