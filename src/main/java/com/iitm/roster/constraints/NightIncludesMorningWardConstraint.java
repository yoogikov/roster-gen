package com.iitm.roster.constraints;

import com.iitm.roster.domain.Assignment;
import com.iitm.roster.domain.Post;
import com.iitm.roster.domain.Roster;
import com.iitm.roster.domain.ShiftSlot;
import com.iitm.roster.domain.ShiftType;
import com.iitm.roster.domain.Staff;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * The night doctor is also the ward doctor on the morning shift of the same day, and the
 * morning ward doctor is that night's doctor. On the CMO's sheet these always go together.
 */
public final class NightIncludesMorningWardConstraint implements Constraint {

	public static final String ID = "night-includes-morning-ward";

	private final ShiftType morning;
	private final ShiftType night;
	private final Severity severity;

	public NightIncludesMorningWardConstraint(ShiftType morning, ShiftType night, Severity severity) {
		this.morning = Objects.requireNonNull(morning, "morning");
		this.night = Objects.requireNonNull(night, "night");
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
		for (LocalDate day : roster.days()) {
			List<Staff> nightDoctors = staffOn(roster, new ShiftSlot(day, night), null);
			List<Staff> morningWard = staffOn(roster, new ShiftSlot(day, morning), Post.WARD);
			for (Staff doctor : nightDoctors) {
				if (!morningWard.contains(doctor)) {
					violations.add(new Violation(ID, severity, day,
							doctor.name() + " is on nights but not ward doctor on shift " + morning.code()));
				}
			}
			for (Staff doctor : morningWard) {
				if (!nightDoctors.isEmpty() && !nightDoctors.contains(doctor)) {
					violations.add(new Violation(ID, severity, day,
							doctor.name() + " is ward doctor on shift " + morning.code() + " but not on nights"));
				}
			}
		}
		return violations;
	}

	/** Staff on {@code slot}, only those in {@code post} if it is not null. */
	private static List<Staff> staffOn(Roster roster, ShiftSlot slot, Post post) {
		return roster.assignmentsOn(slot).stream()
				.filter(a -> post == null || a.post() == post)
				.map(Assignment::staff)
				.toList();
	}

	@Override
	public int penalty(Roster roster) {
		return Penalties.perViolation(severity, check(roster));
	}

}
