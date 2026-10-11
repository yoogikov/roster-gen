package com.iitm.roster.repo;

import static com.iitm.roster.repo.RepoFixtures.DOC_A;
import static com.iitm.roster.repo.RepoFixtures.DOC_B;
import static com.iitm.roster.repo.RepoFixtures.MORNING;
import static com.iitm.roster.repo.RepoFixtures.NIGHT;
import static com.iitm.roster.repo.RepoFixtures.NURSE_C;
import static com.iitm.roster.repo.RepoFixtures.on;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.iitm.roster.domain.Assignment;
import com.iitm.roster.domain.DayOff;
import com.iitm.roster.domain.Post;
import com.iitm.roster.domain.Roster;
import com.iitm.roster.domain.RosterStatus;
import com.iitm.roster.domain.ShiftSlot;
import com.iitm.roster.domain.Staff;
import com.iitm.roster.domain.StaffRole;
import java.time.YearMonth;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;

@DataJpaTest
@Import({RosterStore.class, StaffStore.class})
class RosterRepositoryTest {

	private static final YearMonth JULY = YearMonth.of(2026, 7);

	@Autowired
	private RosterStore rosters;

	@Autowired
	private StaffStore staff;

	@Autowired
	private TestEntityManager em;

	@BeforeEach
	void saveStaff() {
		staff.save(DOC_A);
		staff.save(DOC_B);
		staff.save(NURSE_C);
	}

	@Test
	void rosterSurvivesSaveAndReload() {
		Roster roster = new Roster(JULY, RosterStatus.DRAFT, List.of(
				on(JULY.atDay(14), MORNING, DOC_B),
				on(JULY.atDay(14), MORNING, NURSE_C),
				on(JULY.atDay(1), MORNING, DOC_A),
				on(JULY.atEndOfMonth(), NIGHT, DOC_A)));

		rosters.save(roster);
		flushAndClear();

		assertThat(rosters.findByMonth(JULY)).contains(roster);
	}

	@Test
	void emptyRosterSurvivesSaveAndReload() {
		rosters.save(Roster.empty(JULY));
		flushAndClear();

		assertThat(rosters.findByMonth(JULY)).contains(Roster.empty(JULY));
	}

	@Test
	void savingTheSameMonthAgainReplacesTheRoster() {
		rosters.save(new Roster(JULY, RosterStatus.DRAFT, List.of(
				on(JULY.atDay(1), MORNING, DOC_A), on(JULY.atDay(2), MORNING, DOC_B))));
		Roster published = new Roster(JULY, RosterStatus.PUBLISHED, List.of(on(JULY.atDay(3), NIGHT, NURSE_C)));

		rosters.save(published);
		flushAndClear();

		assertThat(rosters.findByMonth(JULY)).contains(published);
		assertThat(em.getEntityManager().createQuery("select count(r) from RosterEntity r", Long.class)
				.getSingleResult()).isEqualTo(1L);
	}

	@Test
	void resavingInADifferentOrderKeepsTheNewOrder() {
		Assignment first = on(JULY.atDay(1), MORNING, DOC_A);
		Assignment second = on(JULY.atDay(2), MORNING, DOC_B);
		Assignment third = on(JULY.atDay(3), MORNING, NURSE_C);
		rosters.save(new Roster(JULY, RosterStatus.DRAFT, List.of(first, second, third)));
		flushAndClear();
		Roster reordered = new Roster(JULY, RosterStatus.DRAFT, List.of(third, first, second));

		rosters.save(reordered);
		flushAndClear();

		assertThat(rosters.findByMonth(JULY)).contains(reordered);
	}

	@Test
	void monthsAcrossTheYearEndAreKeptApart() {
		YearMonth december = YearMonth.of(2026, 12);
		YearMonth january = YearMonth.of(2027, 1);
		Roster dec = new Roster(december, RosterStatus.DRAFT, List.of(on(december.atEndOfMonth(), NIGHT, DOC_A)));
		Roster jan = new Roster(january, RosterStatus.DRAFT, List.of(on(january.atDay(1), NIGHT, DOC_B)));

		rosters.save(dec);
		rosters.save(jan);
		flushAndClear();

		assertThat(rosters.findByMonth(december)).contains(dec);
		assertThat(rosters.findByMonth(january)).contains(jan);
	}

	@Test
	void postsAndWeeklyOffsSurviveSaveAndReload() {
		Roster roster = new Roster(JULY, RosterStatus.DRAFT,
				List.of(new Assignment(new ShiftSlot(JULY.atDay(6), MORNING), DOC_A, Post.WARD),
						new Assignment(new ShiftSlot(JULY.atDay(6), MORNING), DOC_B, Post.CASUALTY),
						on(JULY.atDay(6), MORNING, NURSE_C)),
				List.of(new DayOff(DOC_A, JULY.atDay(4)), new DayOff(DOC_B, JULY.atDay(5))));

		rosters.save(roster);
		flushAndClear();

		assertThat(rosters.findByMonth(JULY)).contains(roster);
	}

	@Test
	void resavingWithoutWeeklyOffsRemovesThem() {
		rosters.save(new Roster(JULY, RosterStatus.DRAFT, List.of(), List.of(new DayOff(DOC_A, JULY.atDay(4)))));
		flushAndClear();

		rosters.save(Roster.empty(JULY));
		flushAndClear();

		assertThat(rosters.findByMonth(JULY)).contains(Roster.empty(JULY));
	}

	@Test
	void weeklyOffOfUnsavedStaffIsRejected() {
		Staff stranger = new Staff("S99", "Test Unsaved", StaffRole.DOCTOR);
		Roster roster = new Roster(JULY, RosterStatus.DRAFT, List.of(), List.of(new DayOff(stranger, JULY.atDay(4))));

		assertThatThrownBy(() -> rosters.save(roster))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("S99");
	}

	@Test
	void monthNeverSavedIsEmpty() {
		assertThat(rosters.findByMonth(JULY)).isEmpty();
	}

	@Test
	void assignmentOfUnsavedStaffIsRejected() {
		Staff stranger = new Staff("S99", "Test Unsaved", StaffRole.NURSE);
		Roster roster = new Roster(JULY, RosterStatus.DRAFT, List.of(on(JULY.atDay(1), MORNING, stranger)));

		assertThatThrownBy(() -> rosters.save(roster))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("S99");
	}

	@Test
	void assignmentWhoseStaffDiffersFromStoredRecordIsRejected() {
		Staff renamed = new Staff(DOC_A.id(), "Test Doctor A Renamed", DOC_A.role());
		Roster roster = new Roster(JULY, RosterStatus.DRAFT, List.of(on(JULY.atDay(1), MORNING, renamed)));

		assertThatThrownBy(() -> rosters.save(roster))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("does not match");
	}

	private void flushAndClear() {
		em.flush();
		em.clear();
	}

}
