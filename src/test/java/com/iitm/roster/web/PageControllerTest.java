package com.iitm.roster.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.iitm.roster.constraints.Severity;
import com.iitm.roster.constraints.Violation;
import com.iitm.roster.domain.Actor;
import com.iitm.roster.domain.Assignment;
import com.iitm.roster.domain.HospitalCalendar;
import com.iitm.roster.domain.Leave;
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
import com.iitm.roster.scheduler.GenerationResult;
import com.iitm.roster.scheduler.MonthInput;
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
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
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

	/** A mock returns null; the real service returns no holidays for a month never generated. */
	@BeforeEach
	void noSavedHolidays() {
		when(generation.calendarFor(any(YearMonth.class))).thenReturn(HospitalCalendar.noHolidays());
	}

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

	@Test
	void leaveTypedOnTheGeneratePageGoesIntoTheMonthInput() throws Exception {
		when(generation.generate(any(MonthInput.class), any(Actor.class)))
				.thenReturn(new GenerationResult.Ok(Roster.empty(YearMonth.of(2026, 12)), 0));

		mockMvc.perform(post("/rosters/generate").with(httpBasic("admin", PASSWORD))
						.param("month", "2026-12").param("doctors", "D01,Test Doctor 1\nD02,Test Doctor 2\nD03,Test Doctor 3")
						.param("firstOnNights", "D01").param("weekday", "2").param("weekend", "2")
						.param("leave", "D02, 2026-12-15, 2026-12-17\n\nD03,2026-12-24,2026-12-24"))
				.andExpect(status().isOk())
				.andExpect(content().string(org.hamcrest.Matchers.containsString("generated 2026-12")));

		ArgumentCaptor<MonthInput> input = ArgumentCaptor.forClass(MonthInput.class);
		verify(generation).generate(input.capture(), any(Actor.class));
		assertThat(input.getValue().leaves()).containsExactly(
				new Leave(new Staff("D02", "Test Doctor 2", StaffRole.DOCTOR), LocalDate.of(2026, 12, 15), LocalDate.of(2026, 12, 17)),
				new Leave(new Staff("D03", "Test Doctor 3", StaffRole.DOCTOR), LocalDate.of(2026, 12, 24), LocalDate.of(2026, 12, 24)));
	}

	@Test
	void aBadLeaveLineIsReportedAndNothingIsGenerated() throws Exception {
		mockMvc.perform(post("/rosters/generate").with(httpBasic("admin", PASSWORD))
						.param("month", "2026-12").param("doctors", "D01,Test Doctor 1\nD02,Test Doctor 2")
						.param("firstOnNights", "D01").param("weekday", "2").param("weekend", "2")
						.param("leave", "D09,2026-12-15,2026-12-17"))
				.andExpect(status().isOk())
				.andExpect(content().string(org.hamcrest.Matchers.containsString("leave for D09, who is not in the doctor list")));

		verify(generation, never()).generate(any(MonthInput.class), any(Actor.class));
	}

	@Test
	void holidaysTypedOnTheGeneratePageGoIntoTheCalendar() throws Exception {
		MonthInput input = generateWithHolidays("2026-12-02, 2026-12-25\n2026-12-31");

		assertThat(input.calendar().holidays()).containsExactlyInAnyOrder(
				LocalDate.of(2026, 12, 2), LocalDate.of(2026, 12, 25), LocalDate.of(2026, 12, 31));
	}

	@Test
	void aBlankHolidayBoxKeepsTheMonthsSavedHolidays() throws Exception {
		when(generation.calendarFor(YearMonth.of(2026, 12)))
				.thenReturn(new HospitalCalendar(Set.of(LocalDate.of(2026, 12, 25))));

		MonthInput input = generateWithHolidays("  ");

		assertThat(input.calendar().holidays()).containsExactly(LocalDate.of(2026, 12, 25));
	}

	@Test
	void noneClearsTheMonthsSavedHolidays() throws Exception {
		when(generation.calendarFor(YearMonth.of(2026, 12)))
				.thenReturn(new HospitalCalendar(Set.of(LocalDate.of(2026, 12, 25))));

		MonthInput input = generateWithHolidays("none");

		assertThat(input.calendar().holidays()).isEmpty();
	}

	@Test
	void aHolidayThatIsNotADateIsReportedAndNothingIsGenerated() throws Exception {
		postGenerate("25 Dec")
				.andExpect(content().string(org.hamcrest.Matchers.containsString("holidays are yyyy-MM-dd: 25 Dec")));

		verify(generation, never()).generate(any(MonthInput.class), any(Actor.class));
	}

	@Test
	void aHolidayOutsideTheMonthIsReportedAndNothingIsGenerated() throws Exception {
		postGenerate("2027-01-01")
				.andExpect(content().string(org.hamcrest.Matchers.containsString("holiday 2027-01-01 is not in 2026-12")));

		verify(generation, never()).generate(any(MonthInput.class), any(Actor.class));
	}

	private MonthInput generateWithHolidays(String holidays) throws Exception {
		when(generation.generate(any(MonthInput.class), any(Actor.class)))
				.thenReturn(new GenerationResult.Ok(Roster.empty(YearMonth.of(2026, 12)), 0));
		postGenerate(holidays).andExpect(content().string(org.hamcrest.Matchers.containsString("generated 2026-12")));
		ArgumentCaptor<MonthInput> input = ArgumentCaptor.forClass(MonthInput.class);
		verify(generation).generate(input.capture(), any(Actor.class));
		return input.getValue();
	}

	private org.springframework.test.web.servlet.ResultActions postGenerate(String holidays) throws Exception {
		return mockMvc.perform(post("/rosters/generate").with(httpBasic("admin", PASSWORD))
						.param("month", "2026-12").param("doctors", "D01,Test Doctor 1\nD02,Test Doctor 2")
						.param("firstOnNights", "D01").param("weekday", "2").param("weekend", "2")
						.param("holidays", holidays))
				.andExpect(status().isOk());
	}

	@Test
	void aBlankFirstOnNightsContinuesLastMonthsRotation() throws Exception {
		Staff d02 = new Staff("D02", "Test Doctor 2", StaffRole.DOCTOR);
		when(generation.continueNights(any(YearMonth.class), any())).thenReturn(Optional.of(d02));
		when(generation.generate(any(MonthInput.class), any(Actor.class)))
				.thenReturn(new GenerationResult.Ok(Roster.empty(YearMonth.of(2026, 12)), 0));

		postGenerateFirst("")
				.andExpect(content().string(org.hamcrest.Matchers.containsString("nights start with D02, continuing from 2026-11")));

		ArgumentCaptor<MonthInput> input = ArgumentCaptor.forClass(MonthInput.class);
		verify(generation).generate(input.capture(), any(Actor.class));
		assertThat(input.getValue().firstOnNights()).isEqualTo(d02);
	}

	@Test
	void aBlankFirstOnNightsWithNoLastMonthAsksForOneAndGeneratesNothing() throws Exception {
		when(generation.continueNights(any(YearMonth.class), any())).thenReturn(Optional.empty());

		postGenerateFirst(" ")
				.andExpect(content().string(org.hamcrest.Matchers.containsString(
						"no roster for 2026-11 to continue the nights from; type who does the first night")));

		verify(generation, never()).generate(any(MonthInput.class), any(Actor.class));
	}

	@Test
	void aTypedFirstOnNightsIsUsedEvenWhenLastMonthCouldBeContinued() throws Exception {
		when(generation.continueNights(any(YearMonth.class), any()))
				.thenReturn(Optional.of(new Staff("D02", "Test Doctor 2", StaffRole.DOCTOR)));
		when(generation.generate(any(MonthInput.class), any(Actor.class)))
				.thenReturn(new GenerationResult.Ok(Roster.empty(YearMonth.of(2026, 12)), 0));

		postGenerateFirst("D03");

		ArgumentCaptor<MonthInput> input = ArgumentCaptor.forClass(MonthInput.class);
		verify(generation).generate(input.capture(), any(Actor.class));
		assertThat(input.getValue().firstOnNights().id()).isEqualTo("D03");
	}

	private org.springframework.test.web.servlet.ResultActions postGenerateFirst(String first) throws Exception {
		return mockMvc.perform(post("/rosters/generate").with(httpBasic("admin", PASSWORD))
						.param("month", "2026-12").param("doctors", "D01,Test Doctor 1\nD02,Test Doctor 2\nD03,Test Doctor 3")
						.param("firstOnNights", first).param("weekday", "2").param("weekend", "2"))
				.andExpect(status().isOk());
	}

	@Test
	void hardRulesTheGeneratedMonthBreaksAreShownWithTheResult() throws Exception {
		when(generation.brokenRules(YearMonth.of(2026, 12))).thenReturn(List.of(
				new Violation("weekly-off", Severity.HARD, LocalDate.of(2026, 12, 21),
						"Test Doctor 10 has no weekly off in the week of 2026-12-21")));

		when(generation.generate(any(MonthInput.class), any(Actor.class)))
				.thenReturn(new GenerationResult.Ok(Roster.empty(YearMonth.of(2026, 12)), 0));

		postGenerate("none")
				.andExpect(content().string(org.hamcrest.Matchers.containsString(
						"1 HARD rule broken: Test Doctor 10 has no weekly off in the week of 2026-12-21")));
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
