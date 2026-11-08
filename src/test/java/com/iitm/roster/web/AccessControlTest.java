package com.iitm.roster.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.iitm.roster.constraints.Severity;
import com.iitm.roster.constraints.Violation;
import com.iitm.roster.scheduler.GenerationResult;
import com.iitm.roster.domain.ChangeRequest;
import com.iitm.roster.domain.Post;
import com.iitm.roster.domain.RequestState;
import com.iitm.roster.domain.Roster;
import com.iitm.roster.domain.RosterStatus;
import com.iitm.roster.export.Format;
import com.iitm.roster.requests.ChangeLifecycle;
import com.iitm.roster.requests.ChangeResult;
import com.iitm.roster.security.CurrentActor;
import com.iitm.roster.security.WebMvcTestUsers;
import com.iitm.roster.security.HospitalAuthorizationPolicy;
import com.iitm.roster.security.SecurityConfig;
import com.iitm.roster.service.EditResult;
import com.iitm.roster.service.RosterExportService;
import com.iitm.roster.service.RosterGenerationService;
import com.iitm.roster.service.RosterQuery;
import com.iitm.roster.service.RosterService;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * STAFF cannot edit a draft; ADMIN can. The roster is a draft, so the policy denies staff
 * before the date matters.
 */
@WebMvcTest(controllers = RosterController.class)
@AutoConfigureMockMvc
@ActiveProfiles("dev")
@Import({SecurityConfig.class, WebMvcTestUsers.class, HospitalAuthorizationPolicy.class, CurrentActor.class,
		AccessControlTest.ClockConfig.class})
@TestPropertySource(properties = "roster.dev-users.password=" + AccessControlTest.PASSWORD)
class AccessControlTest {

	static final String PASSWORD = "test-only-password";

	private static final YearMonth JULY = YearMonth.of(2026, 7);
	private static final String EDIT = """
			{"type":"assign","date":"2026-07-20","shift":"I","staffId":"D01","post":"GENERAL"}
			""";

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private RosterService edits;

	@MockitoBean
	private RosterQuery query;

	@MockitoBean
	private RosterGenerationService generation;

	@MockitoBean
	private RosterExportService exports;

	@MockitoBean
	private ChangeLifecycle changes;

	@BeforeEach
	void draftRoster() {
		Roster draft = Roster.empty(JULY);
		when(query.find(JULY)).thenReturn(Optional.of(draft));
		when(edits.applyEdit(any(), any())).thenReturn(new EditResult.Applied(draft, List.of()));
	}

	@Test
	void staffEditOfADraftIsForbidden() throws Exception {
		mockMvc.perform(post("/api/rosters/2026-07/edits")
				.with(httpBasic("staff1", PASSWORD))
				.contentType(MediaType.APPLICATION_JSON)
				.content(EDIT))
				.andExpect(status().isForbidden());

		verify(edits, never()).applyEdit(any(), any());
	}

	@Test
	void staffEditOfTheirOwnPublishedShiftIsStoredForApproval() throws Exception {
		when(query.find(JULY)).thenReturn(Optional.of(Roster.empty(JULY).withStatus(RosterStatus.PUBLISHED)));
		ChangeRequest pending = new ChangeRequest("c1", JULY, "assign", LocalDate.of(2026, 7, 20), "I", "D01", null, null,
				Post.GENERAL, RequestState.PENDING, Instant.parse("2026-07-01T04:30:00Z"));
		when(changes.file(any(), any())).thenReturn(new ChangeResult.Pending(pending));

		mockMvc.perform(post("/api/rosters/2026-07/edits")
				.with(httpBasic("staff1", PASSWORD))
				.contentType(MediaType.APPLICATION_JSON)
				.content(EDIT))
				.andExpect(status().isAccepted())
				.andExpect(jsonPath("$.state").value("PENDING"));

		verify(edits, never()).applyEdit(any(), any());
	}

	@Test
	void adminEditOfADraftIsAccepted() throws Exception {
		mockMvc.perform(post("/api/rosters/2026-07/edits")
				.with(httpBasic("admin", PASSWORD))
				.contentType(MediaType.APPLICATION_JSON)
				.content(EDIT))
				.andExpect(status().isOk());
	}

	@Test
	void thePdfExportIsServedAsAPdfFile() throws Exception {
		when(exports.export(JULY, Format.PDF)).thenReturn("%PDF-1.7".getBytes(StandardCharsets.US_ASCII));

		mockMvc.perform(get("/api/rosters/2026-07/export").param("format", "pdf").with(httpBasic("admin", PASSWORD)))
				.andExpect(status().isOk())
				.andExpect(content().contentType("application/pdf"))
				.andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=roster-2026-07.pdf"));
	}

	@Test
	void theCsvExportKeepsItsTypeAndName() throws Exception {
		when(exports.export(JULY, Format.CSV)).thenReturn("date\n".getBytes(StandardCharsets.UTF_8));

		mockMvc.perform(get("/api/rosters/2026-07/export").param("format", "csv").with(httpBasic("admin", PASSWORD)))
				.andExpect(status().isOk())
				.andExpect(content().contentType("text/csv"))
				.andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=roster-2026-07.csv"));
	}

	@Test
	void aSignedInBrowserCanDownloadFromTheSheetsLink() throws Exception {
		when(exports.export(JULY, Format.CSV)).thenReturn("date\n".getBytes(StandardCharsets.UTF_8));
		MockHttpSession session = (MockHttpSession) mockMvc.perform(formLogin().user("admin").password(PASSWORD))
				.andReturn().getRequest().getSession();

		mockMvc.perform(get("/rosters/2026-07/export").param("format", "csv").session(session))
				.andExpect(status().isOk())
				.andExpect(content().contentType("text/csv"));
	}

	@Test
	void anUnknownExportFormatIsABadRequest() throws Exception {
		mockMvc.perform(get("/api/rosters/2026-07/export").param("format", "xlsx").with(httpBasic("admin", PASSWORD)))
				.andExpect(status().isBadRequest());
	}

	@Test
	void theJsonGenerateAnswerListsTheHardRulesTheMonthBreaks() throws Exception {
		when(generation.generate(any(), any())).thenReturn(new GenerationResult.Ok(Roster.empty(JULY), 3));
		when(generation.brokenRules(JULY)).thenReturn(List.of(new Violation("weekly-off", Severity.HARD,
				LocalDate.of(2026, 7, 20), "Test Doctor 10 has no weekly off in the week of 2026-07-20")));

		mockMvc.perform(post("/api/rosters/generate").with(httpBasic("admin", PASSWORD))
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"month":"2026-07","doctors":[{"id":"D01","name":"Test Doctor 1","role":"DOCTOR"}],
								 "firstOnNights":{"id":"D01","name":"Test Doctor 1","role":"DOCTOR"},
								 "leaves":[],"calendar":{"holidays":[]},"weekdayPerShift":4,"weekendPerShift":2}
								"""))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.outcome").value("ok"))
				.andExpect(jsonPath("$.broken[0]").value("weekly-off: Test Doctor 10 has no weekly off in the week of 2026-07-20"));
	}

	static class ClockConfig {

		@Bean
		Clock clock() {
			return Clock.fixed(Instant.parse("2026-07-01T04:30:00Z"), ZoneOffset.UTC);
		}

	}

}
