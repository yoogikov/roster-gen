package com.iitm.roster.scheduler;

import static org.assertj.core.api.Assertions.assertThat;

import com.iitm.roster.domain.Leave;
import com.iitm.roster.domain.Staff;
import com.iitm.roster.domain.StaffRole;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.Combinators;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import net.jqwik.api.constraints.IntRange;

/** The night rotation's promises, for any order, first doctor, leave and night before the month. */
class NightRotationProperties {

	@Property(tries = 300)
	void nobodyOnLeaveAndNobodyOnTwoNightsInARow(@ForAll("months") YearMonth month,
			@ForAll @IntRange(min = 2, max = 12) int doctorCount, @ForAll @IntRange(min = 0, max = 11) int firstIndex,
			@ForAll("leaveSeeds") List<int[]> leaveSeeds, @ForAll @IntRange(min = -1, max = 11) int nightBeforeIndex) {
		List<Staff> doctors = doctors(doctorCount);
		Staff first = doctors.get(firstIndex % doctorCount);
		Staff nightBefore = nightBeforeIndex < 0 ? null : doctors.get(nightBeforeIndex % doctorCount);
		List<Leave> leaves = leaves(month, doctors, leaveSeeds);

		Map<LocalDate, Staff> nights = NightRotation.plan(month, doctors, first, leaves, nightBefore);

		for (Map.Entry<LocalDate, Staff> night : nights.entrySet()) {
			LocalDate day = night.getKey();
			Staff doctor = night.getValue();
			assertThat(YearMonth.from(day)).isEqualTo(month);
			assertThat(leaves).noneMatch(leave -> leave.staff().equals(doctor) && leave.covers(day));
			Staff yesterday = day.getDayOfMonth() == 1 ? nightBefore : nights.get(day.minusDays(1));
			assertThat(doctor).isNotEqualTo(yesterday);
			assertThat(doctors).contains(doctor);
		}
	}

	@Provide
	Arbitrary<YearMonth> months() {
		return Arbitraries.integers().between(0, 35).map(i -> YearMonth.of(2026, 1).plusMonths(i));
	}

	/** Each seed: doctor index, first day, length in days (up to 15, so both short and long leave). */
	@Provide
	Arbitrary<List<int[]>> leaveSeeds() {
		return Combinators.combine(Arbitraries.integers().between(0, 11), Arbitraries.integers().between(1, 28),
				Arbitraries.integers().between(1, 15)).as((who, day, length) -> new int[] {who, day, length})
				.list().ofMaxSize(5);
	}

	static List<Leave> leaves(YearMonth month, List<Staff> doctors, List<int[]> seeds) {
		List<Leave> leaves = new ArrayList<>();
		for (int[] seed : seeds) {
			LocalDate start = month.atDay(seed[1]);
			leaves.add(new Leave(doctors.get(seed[0] % doctors.size()), start, start.plusDays(seed[2] - 1L)));
		}
		return leaves;
	}

	static List<Staff> doctors(int count) {
		List<Staff> doctors = new ArrayList<>();
		for (int i = 1; i <= count; i++) {
			doctors.add(new Staff(String.format("D%02d", i), "Test Doctor " + i, StaffRole.DOCTOR));
		}
		return doctors;
	}

}
