package com.iitm.roster.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.iitm.roster.domain.Actor;
import com.iitm.roster.domain.Assignment;
import com.iitm.roster.domain.RequestState;
import com.iitm.roster.domain.Role;
import com.iitm.roster.domain.Roster;
import com.iitm.roster.domain.RosterStatus;
import com.iitm.roster.domain.ShiftSlot;
import com.iitm.roster.domain.Staff;
import com.iitm.roster.domain.StaffRole;
import com.iitm.roster.domain.StandardShifts;
import com.iitm.roster.domain.SwapRequest;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** "Today" is 11 Oct 2026 in Asia/Kolkata. Made-up doctors only. */
class AuthorizationPolicyTest {

	// 20:00 UTC on 10 Oct is 01:30 IST on 11 Oct, so "today" depends on using the hospital zone.
	private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-10T20:00:00Z"), ZoneOffset.UTC);
	private static final YearMonth OCTOBER = YearMonth.of(2026, 10);
	private static final LocalDate TODAY = LocalDate.of(2026, 10, 11);

	private static final Staff D01 = new Staff("D01", "Test Doctor 1", StaffRole.DOCTOR);
	private static final Staff D02 = new Staff("D02", "Test Doctor 2", StaffRole.DOCTOR);

	private static final Actor ADMIN = new Actor("admin", Role.ADMIN, Optional.empty());
	private static final Actor RECEPTION = new Actor("reception", Role.RECEPTION, Optional.empty());
	private static final Actor DOCTOR_1 = new Actor("staff1", Role.STAFF, Optional.of("D01"));
	private static final Actor UNLINKED_STAFF = new Actor("staff9", Role.STAFF, Optional.empty());

	private static final Roster DRAFT = Roster.empty(OCTOBER);
	private static final Roster PUBLISHED = DRAFT.withStatus(RosterStatus.PUBLISHED);

	private static final Decision ALLOW = new Decision.Allow();
	private static final Decision ADMIN_APPROVAL = new Decision.RequiresApproval(Role.ADMIN);

	private final AuthorizationPolicy policy = new HospitalAuthorizationPolicy(CLOCK);

	@Test
	void everyoneMayViewTheRoster() {
		for (Actor actor : new Actor[] {ADMIN, RECEPTION, DOCTOR_1}) {
			assertThat(policy.may(actor, new Action.ViewRoster(), PUBLISHED)).isEqualTo(ALLOW);
		}
	}

	@Test
	void onlyAdminMayGenerateAndNeverOverAPublishedRoster() {
		assertThat(policy.may(ADMIN, new Action.GenerateRoster(), DRAFT)).isEqualTo(ALLOW);
		assertThat(policy.may(ADMIN, new Action.GenerateRoster(), PUBLISHED)).isInstanceOf(Decision.Deny.class);
		assertThat(policy.may(RECEPTION, new Action.GenerateRoster(), DRAFT)).isInstanceOf(Decision.Deny.class);
		assertThat(policy.may(DOCTOR_1, new Action.GenerateRoster(), DRAFT)).isInstanceOf(Decision.Deny.class);
	}

	@Test
	void onlyAdminMayPublish() {
		assertThat(policy.may(ADMIN, new Action.PublishRoster(), DRAFT)).isEqualTo(ALLOW);
		assertThat(policy.may(RECEPTION, new Action.PublishRoster(), DRAFT)).isInstanceOf(Decision.Deny.class);
	}

	@Test
	void adminMayEditDraftPublishedAndPastDates() {
		assertThat(policy.may(ADMIN, edit(TODAY.plusDays(1), "D01"), DRAFT)).isEqualTo(ALLOW);
		assertThat(policy.may(ADMIN, edit(TODAY.plusDays(1), "D01"), PUBLISHED)).isEqualTo(ALLOW);
		assertThat(policy.may(ADMIN, edit(TODAY.minusDays(1), "D01"), PUBLISHED)).isEqualTo(ALLOW);
	}

	@Test
	void nobodyButAdminMayEditADraft() {
		assertThat(policy.may(RECEPTION, edit(TODAY.plusDays(1), "D01"), DRAFT)).isInstanceOf(Decision.Deny.class);
		assertThat(policy.may(DOCTOR_1, edit(TODAY.plusDays(1), "D01"), DRAFT)).isInstanceOf(Decision.Deny.class);
	}

	@Test
	void receptionEditToAPublishedRosterNeedsAdminApproval() {
		assertThat(policy.may(RECEPTION, edit(TODAY.plusDays(1), "D01", "D02"), PUBLISHED)).isEqualTo(ADMIN_APPROVAL);
	}

	@Test
	void staffEditToOwnPublishedShiftNeedsAdminApproval() {
		assertThat(policy.may(DOCTOR_1, edit(TODAY.plusDays(1), "D01"), PUBLISHED)).isEqualTo(ADMIN_APPROVAL);
	}

	@Test
	void staffMayNotMoveSomeoneElsesShift() {
		assertThat(policy.may(DOCTOR_1, edit(TODAY.plusDays(1), "D02"), PUBLISHED)).isInstanceOf(Decision.Deny.class);
		assertThat(policy.may(DOCTOR_1, edit(TODAY.plusDays(1), "D01", "D02"), PUBLISHED))
				.isInstanceOf(Decision.Deny.class);
	}

	@Test
	void staffNotLinkedToARosteredPersonMayNotEdit() {
		assertThat(policy.may(UNLINKED_STAFF, edit(TODAY.plusDays(1), "D01"), PUBLISHED))
				.isInstanceOf(Decision.Deny.class);
	}

	@Test
	void pastDatesAreOnlyForAdminEvenWhenPublished() {
		assertThat(policy.may(RECEPTION, edit(TODAY.minusDays(1), "D01"), PUBLISHED)).isInstanceOf(Decision.Deny.class);
		assertThat(policy.may(DOCTOR_1, edit(TODAY.minusDays(1), "D01"), PUBLISHED)).isInstanceOf(Decision.Deny.class);
	}

	@Test
	void todayIsNotPastInTheHospitalZone() {
		assertThat(policy.may(DOCTOR_1, edit(TODAY, "D01"), PUBLISHED)).isEqualTo(ADMIN_APPROVAL);
	}

	@Test
	void staffMayProposeASwapOfTheirOwnShiftOnAPublishedRoster() {
		assertThat(policy.may(DOCTOR_1, propose(D01, D02, TODAY.plusDays(2)), PUBLISHED)).isEqualTo(ALLOW);
		assertThat(policy.may(RECEPTION, propose(D01, D02, TODAY.plusDays(2)), PUBLISHED)).isEqualTo(ALLOW);
	}

	@Test
	void swapsAreRefusedOnDraftsForPastShiftsAndForOthersShifts() {
		assertThat(policy.may(DOCTOR_1, propose(D01, D02, TODAY.plusDays(2)), DRAFT)).isInstanceOf(Decision.Deny.class);
		assertThat(policy.may(DOCTOR_1, propose(D01, D02, TODAY.minusDays(1)), PUBLISHED))
				.isInstanceOf(Decision.Deny.class);
		assertThat(policy.may(DOCTOR_1, propose(D02, D01, TODAY.plusDays(2)), PUBLISHED))
				.isInstanceOf(Decision.Deny.class);
	}

	@Test
	void onlyThePersonAskedMayAcceptASwap() {
		SwapRequest askingD01 = swap(D02, D01);
		SwapRequest askingD02 = swap(D01, D02);

		assertThat(policy.may(DOCTOR_1, new Action.AcceptSwap(askingD01), PUBLISHED)).isEqualTo(ALLOW);
		assertThat(policy.may(DOCTOR_1, new Action.AcceptSwap(askingD02), PUBLISHED)).isInstanceOf(Decision.Deny.class);
		assertThat(policy.may(ADMIN, new Action.AcceptSwap(askingD01), PUBLISHED)).isInstanceOf(Decision.Deny.class);
	}

	@Test
	void onlyAdminMayApproveOrRejectASwap() {
		SwapRequest request = swap(D01, D02);

		assertThat(policy.may(ADMIN, new Action.DecideSwap(request), PUBLISHED)).isEqualTo(ALLOW);
		assertThat(policy.may(RECEPTION, new Action.DecideSwap(request), PUBLISHED)).isInstanceOf(Decision.Deny.class);
		assertThat(policy.may(DOCTOR_1, new Action.DecideSwap(request), PUBLISHED)).isInstanceOf(Decision.Deny.class);
	}

	@Test
	void aSwapWhoseShiftHasPassedCannotBeDecided() {
		SwapRequest stale = swap(D01, D02, TODAY.minusDays(1));

		assertThat(policy.may(ADMIN, new Action.DecideSwap(stale), PUBLISHED)).isInstanceOf(Decision.Deny.class);
	}

	@Test
	void denialsCarryAReasonForTheUser() {
		assertThat(policy.may(DOCTOR_1, edit(TODAY.plusDays(1), "D02"), PUBLISHED))
				.isEqualTo(new Decision.Deny("staff can only change their own shifts; ask for a swap instead"));
	}

	private static Action.EditShift edit(LocalDate date, String... staffIds) {
		return new Action.EditShift(new ShiftSlot(date, StandardShifts.SHIFT_II), Set.of(staffIds));
	}

	private static Action.ProposeSwap propose(Staff offering, Staff asked, LocalDate date) {
		return new Action.ProposeSwap(new Assignment(new ShiftSlot(date, StandardShifts.SHIFT_I), offering),
				new Assignment(new ShiftSlot(date, StandardShifts.SHIFT_II), asked));
	}

	private static SwapRequest swap(Staff offering, Staff asked) {
		return swap(offering, asked, TODAY.plusDays(2));
	}

	private static SwapRequest swap(Staff offering, Staff asked, LocalDate date) {
		Action.ProposeSwap proposal = propose(offering, asked, date);
		return new SwapRequest("R1", proposal.offered(), proposal.wanted(), RequestState.PENDING,
				Instant.parse("2026-10-10T20:00:00Z"));
	}

}
