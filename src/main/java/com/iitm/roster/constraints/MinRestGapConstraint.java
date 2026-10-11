package com.iitm.roster.constraints;

import com.iitm.roster.domain.Assignment;
import com.iitm.roster.domain.Roster;
import com.iitm.roster.domain.Staff;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Between the end of one shift and the start of the next, each person gets at least
 * {@code minimumRest}. Times are in {@code Asia/Kolkata}, so a night shift ending at 08:00
 * is compared with the next day's shifts correctly.
 *
 * <p>Only shifts inside this roster are compared: a night at the end of the previous month
 * is not seen yet.
 */
public final class MinRestGapConstraint implements Constraint {

	public static final String ID = "min-rest-gap";

	private final Duration minimumRest;
	private final Severity severity;

	public MinRestGapConstraint(Duration minimumRest, Severity severity) {
		Objects.requireNonNull(minimumRest, "minimumRest");
		if (minimumRest.isNegative()) {
			throw new IllegalArgumentException("minimumRest must not be negative: " + minimumRest);
		}
		this.minimumRest = minimumRest;
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
		Map<Staff, List<Assignment>> byStaff = new LinkedHashMap<>();
		for (Assignment assignment : roster.assignments()) {
			byStaff.computeIfAbsent(assignment.staff(), staff -> new ArrayList<>()).add(assignment);
		}
		List<Violation> violations = new ArrayList<>();
		for (List<Assignment> shifts : byStaff.values()) {
			shifts.sort(Comparator.comparing(a -> a.slot().start()));
			for (int i = 1; i < shifts.size(); i++) {
				Assignment previous = shifts.get(i - 1);
				Assignment next = shifts.get(i);
				Duration rest = Duration.between(previous.slot().end(), next.slot().start());
				if (rest.compareTo(minimumRest) < 0) {
					violations.add(new Violation(ID, severity, next.slot().date(),
							String.format("%s gets %d h rest before %s, needs %d h",
									next.staff().name(), rest.toHours(), next.slot().shift().code(), minimumRest.toHours())));
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
