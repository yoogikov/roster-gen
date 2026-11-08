package com.iitm.roster.constraints;

import com.iitm.roster.domain.Assignment;
import com.iitm.roster.domain.Leave;
import com.iitm.roster.domain.Post;
import com.iitm.roster.domain.Roster;
import com.iitm.roster.domain.ShiftType;
import com.iitm.roster.domain.Staff;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * Shares each kind of duty fairly. A doctor's fair share of a duty is the month's total of it,
 * split in proportion to the days each doctor is not on leave. Every duty a doctor has above that
 * share rounded up, or below it rounded down, is one violation.
 */
public final class PostBalanceConstraint implements Constraint {

	public static final String ID = "post-balance";

	/** One kind of duty to share out: a post on a shift. */
	public record Duty(ShiftType shift, Post post) {

		public Duty {
			Objects.requireNonNull(shift, "shift");
			Objects.requireNonNull(post, "post");
		}
	}

	private final List<Duty> duties;
	private final List<Staff> doctors;
	private final List<Leave> leaves;
	private final Severity severity;

	public PostBalanceConstraint(List<Duty> duties, List<Staff> doctors, List<Leave> leaves, Severity severity) {
		this.duties = List.copyOf(duties);
		this.doctors = List.copyOf(doctors);
		this.leaves = List.copyOf(leaves);
		this.severity = Objects.requireNonNull(severity, "severity");
	}

	@Override
	public String id() {
		return ID;
	}

	@Override
	public Severity severity() {
		return severity;
	}

	@Override
	public List<Violation> check(Roster roster) {
		List<Violation> violations = new ArrayList<>();
		LocalDate first = roster.days().get(0);
		int[] available = new int[doctors.size()];
		int allAvailable = 0;
		for (int i = 0; i < available.length; i++) {
			available[i] = daysAvailable(doctors.get(i), roster);
			allAvailable += available[i];
		}
		if (allAvailable == 0) {
			return violations;
		}
		for (Duty duty : duties) {
			int[] counts = counts(duty, roster);
			int total = 0;
			for (int count : counts) {
				total += count;
			}
			for (int i = 0; i < counts.length; i++) {
				// Whole-number arithmetic: the share is total * available[i] / allAvailable.
				int shareFloor = total * available[i] / allAvailable;
				int shareCeiling = (total * available[i] + allAvailable - 1) / allAvailable;
				int off = Math.max(counts[i] - shareCeiling, Math.max(shareFloor - counts[i], 0));
				for (int n = 1; n <= off; n++) {
					violations.add(new Violation(ID, severity, first, String.format(Locale.ROOT,
							"%s has %d %s duties on shift %s; a fair share is %d to %d (%d)", doctors.get(i).name(), counts[i],
							duty.post().name().toLowerCase(Locale.ROOT), duty.shift().code(), shareFloor, shareCeiling, n)));
				}
			}
		}
		return violations;
	}

	private int[] counts(Duty duty, Roster roster) {
		int[] counts = new int[doctors.size()];
		for (Assignment assignment : roster.assignments()) {
			if (assignment.post() == duty.post() && assignment.slot().shift().equals(duty.shift())) {
				int at = doctors.indexOf(assignment.staff());
				if (at >= 0) {
					counts[at]++;
				}
			}
		}
		return counts;
	}

	private int daysAvailable(Staff doctor, Roster roster) {
		return (int) roster.days().stream()
				.filter(day -> leaves.stream().noneMatch(l -> l.staff().equals(doctor) && l.covers(day)))
				.count();
	}

	@Override
	public int penalty(Roster roster) {
		return Penalties.perViolation(severity, check(roster));
	}

}
