package com.iitm.roster.service;

import com.iitm.roster.constraints.Constraint;
import com.iitm.roster.constraints.MinStaffingConstraint;
import com.iitm.roster.constraints.NightIncludesMorningWardConstraint;
import com.iitm.roster.constraints.PostCoverageConstraint;
import com.iitm.roster.constraints.PostDutyOffConstraint;
import com.iitm.roster.constraints.Severity;
import com.iitm.roster.constraints.StaffingPlan;
import com.iitm.roster.constraints.StaffingRequirement;
import com.iitm.roster.domain.HospitalCalendar;
import com.iitm.roster.domain.StaffRole;
import com.iitm.roster.domain.StandardShifts;
import com.iitm.roster.rules.ConstraintFactory;
import com.iitm.roster.rules.InvalidRuleException;
import com.iitm.roster.rules.RuleSpec;
import com.iitm.roster.rules.RuleTemplate;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * The rule shapes the editor can offer. Weekday staffing is the one with parameters;
 * the others are the hospital rules that are already fixed, shown so they can be confirmed.
 */
@Component
public class HospitalRuleCatalog implements ConstraintFactory {

	private static final List<RuleTemplate> TEMPLATES = List.of(
			new RuleTemplate("weekday-staffing",
					"Doctors on each day shift: one number for weekdays, another for weekends and holidays.",
					List.of("weekday", "weekend")),
			new RuleTemplate("night-includes-ward",
					"The night doctor is also that day's Shift I ward doctor.", List.of()),
			new RuleTemplate("post-duty-off", "The day after a night is off.", List.of()),
			new RuleTemplate("ward-and-casualty",
					"Each day shift has exactly one ward doctor and one casualty doctor.", List.of()));

	@Override
	public Constraint build(RuleSpec spec) throws InvalidRuleException {
		return switch (spec.templateId()) {
			case "weekday-staffing" -> staffing(spec.params());
			case "night-includes-ward" -> fixed(spec, new NightIncludesMorningWardConstraint(StandardShifts.SHIFT_I,
					StandardShifts.NIGHT, Severity.HARD));
			case "post-duty-off" -> fixed(spec, new PostDutyOffConstraint(StandardShifts.NIGHT, Severity.HARD));
			case "ward-and-casualty" -> fixed(spec, new PostCoverageConstraint(StandardShifts.DAY_SHIFTS, Severity.HARD));
			default -> throw new InvalidRuleException("unknown template: " + spec.templateId());
		};
	}

	@Override
	public List<RuleTemplate> templates() {
		return TEMPLATES;
	}

	private static Constraint fixed(RuleSpec spec, Constraint constraint) throws InvalidRuleException {
		if (!spec.params().isEmpty()) {
			throw new InvalidRuleException(spec.templateId() + " has no parameters");
		}
		return constraint;
	}

	private static Constraint staffing(Map<String, String> params) throws InvalidRuleException {
		int weekday = number(params, "weekday");
		int weekend = number(params, "weekend");
		if (weekday < 2 || weekend < 2) {
			throw new InvalidRuleException("each day shift needs at least a ward and a casualty doctor");
		}
		StaffingPlan plan = StaffingPlan.weekdayAndWeekend(HospitalCalendar.noHolidays(),
				List.of(new StaffingRequirement(StandardShifts.SHIFT_I, StaffRole.DOCTOR, weekday),
						new StaffingRequirement(StandardShifts.SHIFT_II, StaffRole.DOCTOR, weekday)),
				List.of(new StaffingRequirement(StandardShifts.SHIFT_I, StaffRole.DOCTOR, weekend),
						new StaffingRequirement(StandardShifts.SHIFT_II, StaffRole.DOCTOR, weekend)));
		return new MinStaffingConstraint(plan, Severity.HARD);
	}

	private static int number(Map<String, String> params, String name) throws InvalidRuleException {
		String raw = params.get(name);
		if (raw == null || !raw.trim().chars().allMatch(Character::isDigit) || raw.isBlank()) {
			throw new InvalidRuleException(name + " must be a whole number");
		}
		return Integer.parseInt(raw.trim());
	}

}
