package com.iitm.roster.web;

import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.iitm.roster.domain.Assignment;
import com.iitm.roster.domain.Post;
import com.iitm.roster.domain.Roster;
import com.iitm.roster.domain.ShiftSlot;
import com.iitm.roster.domain.Staff;
import com.iitm.roster.domain.StaffRole;
import com.iitm.roster.domain.StandardShifts;
import com.iitm.roster.requests.ChangeLifecycle;
import com.iitm.roster.requests.LeaveLifecycle;
import com.iitm.roster.requests.RequestInbox;
import com.iitm.roster.requests.SwapLifecycle;
import com.iitm.roster.security.CurrentActor;
import com.iitm.roster.security.DevUsersConfig;
import com.iitm.roster.security.HospitalAuthorizationPolicy;
import com.iitm.roster.security.SecurityConfig;
import com.iitm.roster.service.RosterGenerationService;
import com.iitm.roster.service.RosterQuery;
import com.iitm.roster.service.RosterService;
import com.iitm.roster.service.RuleBook;
import java.time.Clock;
import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/** The sheet is editable for an admin and read-only for staff. */
@WebMvcTest(controllers = PageController.class)
@AutoConfigureMockMvc
@ActiveProfiles("dev")
@Import({SecurityConfig.class, DevUsersConfig.class, HospitalAuthorizationPolicy.class, CurrentActor.class,
		PageControllerTest.ClockConfig.class})
@TestPropertySource(properties = "roster.dev-users.password=" + PageControllerTest.PASSWORD)
class PageControllerTest {

	static final String PASSWORD = "test-only-password";

	private static final YearMonth JULY = YearMonth.of(2026, 7);

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private RosterQuery query;

	@MockitoBean
	private RosterService edits;

	@MockitoBean
	private RosterGenerationService generation;

	@MockitoBean
	private ChangeLifecycle changes;

	@MockitoBean
	private LeaveLifecycle leaves;

	@MockitoBean
	private SwapLifecycle swaps;

	@MockitoBean
	private RequestInbox inbox;

	@MockitoBean
	private RuleBook rules;

	@Test
	void staffSeeTheSheetWithoutTheEditForm() throws Exception {
		when(query.find(JULY)).thenReturn(Optional.of(sample()));

		mockMvc.perform(get("/rosters/2026-07").with(httpBasic("staff1", PASSWORD)))
				.andExpect(status().isOk())
				.andExpect(content().string(org.hamcrest.Matchers.containsString("Test Doctor 1 W")))
				.andExpect(content().string(org.hamcrest.Matchers.containsString("Post-duty off")))
				.andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("edit-form"))));
	}

	@Test
	void adminGetsTheCellEditForm() throws Exception {
		when(query.find(JULY)).thenReturn(Optional.of(sample()));

		mockMvc.perform(get("/rosters/2026-07").with(httpBasic("admin", PASSWORD)))
				.andExpect(status().isOk())
				.andExpect(content().string(org.hamcrest.Matchers.containsString("id=\"edit-form\"")))
				.andExpect(content().string(org.hamcrest.Matchers.containsString("roster-grid.js")));
	}

	@Test
	void staffHomeIsReadOnly() throws Exception {
		mockMvc.perform(get("/").with(httpBasic("staff1", PASSWORD)))
				.andExpect(status().isOk())
				.andExpect(content().string(org.hamcrest.Matchers.containsString("read-only")));
	}

	private static Roster sample() {
		Staff doctor = new Staff("D01", "Test Doctor 1", StaffRole.DOCTOR);
		return Roster.empty(JULY).withAssignment(
				new Assignment(new ShiftSlot(JULY.atDay(2), StandardShifts.SHIFT_I), doctor, Post.WARD));
	}

	static class ClockConfig {

		@Bean
		Clock clock() {
			return Clock.fixed(Instant.parse("2026-07-01T04:30:00Z"), ZoneOffset.UTC);
		}

	}

}
