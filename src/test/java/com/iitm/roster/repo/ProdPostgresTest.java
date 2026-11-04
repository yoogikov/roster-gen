package com.iitm.roster.repo;

import static org.assertj.core.api.Assertions.assertThat;

import com.iitm.roster.domain.Assignment;
import com.iitm.roster.domain.DayOff;
import com.iitm.roster.domain.Post;
import com.iitm.roster.domain.Roster;
import com.iitm.roster.domain.RosterStatus;
import com.iitm.roster.domain.ShiftSlot;
import com.iitm.roster.domain.Staff;
import com.iitm.roster.domain.StaffRole;
import com.iitm.roster.domain.StandardShifts;
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import java.io.IOException;
import java.time.YearMonth;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * The prod profile against a real Postgres. Hibernate creates the schema for this test only;
 * a deployed prod database is still validate-only and is not migrated yet.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("prod")
class ProdPostgresTest {

	private static final EmbeddedPostgres POSTGRES = start();

	@Autowired
	private StaffStore staff;

	@Autowired
	private RosterStore rosters;

	@DynamicPropertySource
	static void datasource(DynamicPropertyRegistry registry) {
		registry.add("spring.datasource.url", () -> POSTGRES.getJdbcUrl("postgres", "postgres"));
		registry.add("spring.datasource.username", () -> "postgres");
		registry.add("spring.datasource.password", () -> "postgres");
		registry.add("spring.jpa.hibernate.ddl-auto", () -> "update");
	}

	@Test
	void aRosterRoundTripsWithItsOrderKept() {
		YearMonth july = YearMonth.of(2026, 7);
		Staff first = new Staff("D01", "Test Doctor 1", StaffRole.DOCTOR);
		Staff second = new Staff("D02", "Test Doctor 2", StaffRole.DOCTOR);
		staff.save(first);
		staff.save(second);
		Roster roster = new Roster(july, RosterStatus.PUBLISHED, List.of(
				new Assignment(new ShiftSlot(july.atDay(2), StandardShifts.SHIFT_I), first, Post.WARD),
				new Assignment(new ShiftSlot(july.atDay(3), StandardShifts.SHIFT_II), second, Post.CASUALTY)),
				List.of(new DayOff(second, july.atDay(4)), new DayOff(first, july.atDay(5))));
		rosters.save(roster);

		assertThat(rosters.findByMonth(july)).contains(roster);
	}

	private static EmbeddedPostgres start() {
		try {
			return EmbeddedPostgres.builder().start();
		} catch (IOException ex) {
			throw new IllegalStateException("could not start Postgres", ex);
		}
	}

}
