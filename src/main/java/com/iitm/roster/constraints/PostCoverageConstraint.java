package com.iitm.roster.constraints;

import com.iitm.roster.domain.Assignment;
import com.iitm.roster.domain.Post;
import com.iitm.roster.domain.Roster;
import com.iitm.roster.domain.ShiftSlot;
import com.iitm.roster.domain.ShiftType;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Each of the given shifts has exactly one ward doctor and exactly one casualty doctor every day. */
public final class PostCoverageConstraint implements Constraint {

	public static final String ID = "ward-and-casualty";

	private static final List<Post> COVERED = List.of(Post.WARD, Post.CASUALTY);

	private final List<ShiftType> shifts;
	private final Severity severity;

	public PostCoverageConstraint(List<ShiftType> shifts, Severity severity) {
		this.shifts = List.copyOf(shifts);
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
			for (ShiftType shift : shifts) {
				List<Assignment> onShift = roster.assignmentsOn(new ShiftSlot(day, shift));
				for (Post post : COVERED) {
					long count = onShift.stream().filter(a -> a.post() == post).count();
					if (count == 0) {
						violations.add(new Violation(ID, severity, day, String.format("no %s doctor on shift %s", post, shift.code())));
					} else if (count > 1) {
						violations.add(new Violation(ID, severity, day, String.format("%d %s doctors on shift %s", count, post, shift.code())));
					}
				}
			}
		}
		return violations;
	}

	@Override
	public int penalty(Roster roster) {
		return Penalties.perViolation(severity, check(roster));
	}

}
