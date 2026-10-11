package com.iitm.roster.security;

import com.iitm.roster.domain.Actor;
import com.iitm.roster.domain.HospitalTime;
import com.iitm.roster.domain.Role;
import com.iitm.roster.domain.Roster;
import com.iitm.roster.domain.RosterStatus;
import com.iitm.roster.domain.SwapRequest;
import java.time.Clock;
import java.time.LocalDate;
import java.util.Objects;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * The hospital's rules for who may do what. "Past" means a date before today in
 * {@code Asia/Kolkata}.
 *
 * <table>
 *   <caption>Who may do what</caption>
 *   <tr><th>Action</th><th>ADMIN</th><th>RECEPTION</th><th>STAFF</th></tr>
 *   <tr><td>view</td><td>yes</td><td>yes</td><td>yes</td></tr>
 *   <tr><td>generate</td><td>yes, unless published</td><td>no</td><td>no</td></tr>
 *   <tr><td>publish</td><td>yes</td><td>no</td><td>no</td></tr>
 *   <tr><td>edit a past date</td><td>yes</td><td>no</td><td>no</td></tr>
 *   <tr><td>edit a draft</td><td>yes</td><td>no</td><td>no</td></tr>
 *   <tr><td>edit a published roster</td><td>yes</td><td>needs ADMIN approval</td>
 *       <td>own shifts only, needs ADMIN approval</td></tr>
 *   <tr><td>propose a swap (published, not past)</td><td>yes</td><td>yes</td><td>own shift only</td></tr>
 *   <tr><td>accept a swap</td><td>no</td><td>no</td><td>only the person asked</td></tr>
 *   <tr><td>approve or reject a swap (not past)</td><td>yes</td><td>no</td><td>no</td></tr>
 * </table>
 */
@Component
public final class HospitalAuthorizationPolicy implements AuthorizationPolicy {

	private static final Decision ALLOW = new Decision.Allow();
	private static final Decision ADMIN_APPROVAL = new Decision.RequiresApproval(Role.ADMIN);

	private final Clock clock;

	public HospitalAuthorizationPolicy(Clock clock) {
		this.clock = Objects.requireNonNull(clock, "clock");
	}

	@Override
	public Decision may(Actor actor, Action action, Roster target) {
		Objects.requireNonNull(actor, "actor");
		Objects.requireNonNull(action, "action");
		Objects.requireNonNull(target, "target");
		return switch (action) {
			case Action.ViewRoster() -> ALLOW;
			case Action.GenerateRoster() -> generate(actor, target);
			case Action.PublishRoster() -> adminOnly(actor, "only an admin can publish a roster");
			case Action.EditShift edit -> edit(actor, edit, target);
			case Action.ProposeSwap propose -> propose(actor, propose, target);
			case Action.AcceptSwap accept -> isStaff(actor, accept.request().wanted().staff().id())
					? ALLOW
					: new Decision.Deny("only the person asked can accept a swap");
			case Action.DecideSwap(SwapRequest request) -> decide(actor, request);
		};
	}

	private Decision generate(Actor actor, Roster target) {
		if (actor.role() != Role.ADMIN) {
			return new Decision.Deny("only an admin can generate a roster");
		}
		if (target.status() == RosterStatus.PUBLISHED) {
			return new Decision.Deny(target.month() + " is published; it cannot be regenerated");
		}
		return ALLOW;
	}

	private Decision edit(Actor actor, Action.EditShift edit, Roster target) {
		if (actor.role() == Role.ADMIN) {
			return ALLOW;
		}
		if (isPast(edit.slot().date())) {
			return new Decision.Deny("only an admin can change a past date");
		}
		if (target.status() == RosterStatus.DRAFT) {
			return new Decision.Deny("only an admin can edit a draft roster");
		}
		if (actor.role() == Role.RECEPTION) {
			return ADMIN_APPROVAL;
		}
		return isOnlyOwnShifts(actor, edit.staffIds())
				? ADMIN_APPROVAL
				: new Decision.Deny("staff can only change their own shifts; ask for a swap instead");
	}

	private Decision propose(Actor actor, Action.ProposeSwap propose, Roster target) {
		if (target.status() != RosterStatus.PUBLISHED) {
			return new Decision.Deny("swaps are only for a published roster");
		}
		if (isPast(propose.offered().slot().date()) || isPast(propose.wanted().slot().date())) {
			return new Decision.Deny("a past shift cannot be swapped");
		}
		if (actor.role() == Role.STAFF && !isStaff(actor, propose.offered().staff().id())) {
			return new Decision.Deny("staff can only offer their own shift");
		}
		return ALLOW;
	}

	private Decision decide(Actor actor, SwapRequest request) {
		if (actor.role() != Role.ADMIN) {
			return new Decision.Deny("only an admin can approve or reject a swap");
		}
		if (isPast(request.offered().slot().date()) || isPast(request.wanted().slot().date())) {
			return new Decision.Deny("a past shift cannot be swapped");
		}
		return ALLOW;
	}

	private static Decision adminOnly(Actor actor, String reason) {
		return actor.role() == Role.ADMIN ? ALLOW : new Decision.Deny(reason);
	}

	private static boolean isStaff(Actor actor, String staffId) {
		return actor.staffId().filter(staffId::equals).isPresent();
	}

	private static boolean isOnlyOwnShifts(Actor actor, Set<String> staffIds) {
		return actor.staffId().map(own -> staffIds.equals(Set.of(own))).orElse(false);
	}

	private boolean isPast(LocalDate date) {
		return date.isBefore(LocalDate.now(clock.withZone(HospitalTime.ZONE)));
	}

}
