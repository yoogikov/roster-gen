package com.iitm.roster.scheduler;

import com.iitm.roster.constraints.Constraint;
import com.iitm.roster.constraints.MinStaffingConstraint;
import com.iitm.roster.constraints.NightIncludesMorningWardConstraint;
import com.iitm.roster.constraints.NoLeaveConflictConstraint;
import com.iitm.roster.constraints.PostCoverageConstraint;
import com.iitm.roster.constraints.PostDutyOffConstraint;
import com.iitm.roster.constraints.Severity;
import com.iitm.roster.constraints.StaffingPlan;
import com.iitm.roster.constraints.StaffingRequirement;
import com.iitm.roster.constraints.WeeklyOffConstraint;
import com.iitm.roster.constraints.WeeklyShiftBlockConstraint;
import com.iitm.roster.domain.Assignment;
import com.iitm.roster.domain.DayOff;
import com.iitm.roster.domain.Post;
import com.iitm.roster.domain.Roster;
import com.iitm.roster.domain.ShiftSlot;
import com.iitm.roster.domain.ShiftType;
import com.iitm.roster.domain.Staff;
import com.iitm.roster.domain.StaffRole;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Rosters a month of doctors under the IITM Hospital rules, in three steps:
 * <ol>
 *   <li>nights from the fixed rotation ({@link NightRotation}); each night doctor is also
 *       the Shift I ward doctor that day;
 *   <li>weekly offs ({@link WeeklyOffPlanner});
 *   <li>everything else (a ward and a casualty doctor on each day shift, then general duty
 *       up to the minimum) with the {@link GreedyRosterGenerator}.
 * </ol>
 * Post-duty off is not stored: it is the day after a night, enforced by
 * {@link PostDutyOffConstraint}.
 */
public final class HospitalRosterGenerator {

	private final ShiftType morning;
	private final ShiftType afternoon;
	private final ShiftType night;

	public HospitalRosterGenerator(ShiftType morning, ShiftType afternoon, ShiftType night) {
		this.morning = morning;
		this.afternoon = afternoon;
		this.night = night;
	}

	public GenerationResult generate(MonthInput input) {
		StaffingPlan dayShifts = staffing(input);
		List<Constraint> constraints = constraints(input);

		Map<LocalDate, Staff> nights = NightRotation.plan(input.month(), input.doctors(), input.firstOnNights(), input.leaves());
		List<DayOff> offs = new WeeklyOffPlanner(dayShifts, List.of(morning, afternoon))
				.plan(input.month(), input.doctors(), nights, input.leaves());

		Roster start = Roster.empty(input.month()).withWeeklyOffs(offs);
		List<ShiftSlot> unplannedNights = new ArrayList<>();
		for (LocalDate day : start.days()) {
			Staff doctor = nights.get(day);
			if (doctor == null) {
				unplannedNights.add(new ShiftSlot(day, night));
				continue;
			}
			start = start.withAssignment(new Assignment(new ShiftSlot(day, night), doctor))
					.withAssignment(new Assignment(new ShiftSlot(day, morning), doctor, Post.WARD));
		}

		GenerationRequest request = new GenerationRequest(start, input.doctors(), openSlots(start, dayShifts), constraints);
		GenerationResult result = new GreedyRosterGenerator().generate(request);
		if (unplannedNights.isEmpty()) {
			return result;
		}
		List<ShiftSlot> unfilled = new ArrayList<>(unplannedNights);
		Roster roster;
		if (result instanceof GenerationResult.Infeasible infeasible) {
			unfilled.addAll(infeasible.unfilled());
			roster = infeasible.partial();
		} else {
			roster = ((GenerationResult.Ok) result).roster();
		}
		return new GenerationResult.Infeasible(roster, unfilled);
	}

	/** Minimum doctors on each day shift: one number for weekdays, another for weekends and holidays. */
	public StaffingPlan staffing(MonthInput input) {
		return StaffingPlan.weekdayAndWeekend(input.calendar(),
				perShift(input.weekdayPerShift()), perShift(input.weekendPerShift()));
	}

	/** The rules, all HARD except the weekly morning/afternoon block. */
	public List<Constraint> constraints(MonthInput input) {
		return List.of(
				new MinStaffingConstraint(staffing(input), Severity.HARD),
				new PostCoverageConstraint(List.of(morning, afternoon), Severity.HARD),
				new NightIncludesMorningWardConstraint(morning, night, Severity.HARD),
				new PostDutyOffConstraint(night, Severity.HARD),
				new NoLeaveConflictConstraint(input.leaves(), Severity.HARD),
				new WeeklyOffConstraint(night, input.leaves(), Severity.HARD),
				new WeeklyShiftBlockConstraint(morning, afternoon, Severity.SOFT));
	}

	private List<StaffingRequirement> perShift(int minimum) {
		return List.of(new StaffingRequirement(morning, StaffRole.DOCTOR, minimum),
				new StaffingRequirement(afternoon, StaffRole.DOCTOR, minimum));
	}

	/** A ward and a casualty place on each day shift if missing, then general places up to the minimum. */
	private List<OpenSlot> openSlots(Roster roster, StaffingPlan dayShifts) {
		List<OpenSlot> open = new ArrayList<>();
		for (LocalDate day : roster.days()) {
			for (StaffingRequirement requirement : dayShifts.on(day)) {
				ShiftSlot slot = new ShiftSlot(day, requirement.shift());
				List<Assignment> present = roster.assignmentsOn(slot);
				int places = present.size();
				for (Post post : List.of(Post.WARD, Post.CASUALTY)) {
					if (present.stream().noneMatch(a -> a.post() == post)) {
						open.add(new OpenSlot(slot, StaffRole.DOCTOR, post));
						places++;
					}
				}
				for (int i = places; i < requirement.minimum(); i++) {
					open.add(new OpenSlot(slot, StaffRole.DOCTOR, Post.GENERAL));
				}
			}
		}
		return open;
	}

}
