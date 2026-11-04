package com.iitm.roster.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.iitm.roster.domain.Roster;
import com.iitm.roster.security.CurrentActor;
import com.iitm.roster.security.DevUsersConfig;
import com.iitm.roster.security.HospitalAuthorizationPolicy;
import com.iitm.roster.security.SecurityConfig;
import com.iitm.roster.service.EditResult;
import com.iitm.roster.service.RosterExportService;
import com.iitm.roster.service.RosterGenerationService;
import com.iitm.roster.service.RosterQuery;
import com.iitm.roster.service.RosterService;
import java.time.Clock;
import java.time.Instant;
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
import org.springframework.http.MediaType;
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
@Import({SecurityConfig.class, DevUsersConfig.class, HospitalAuthorizationPolicy.class, CurrentActor.class,
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
	void adminEditOfADraftIsAccepted() throws Exception {
		mockMvc.perform(post("/api/rosters/2026-07/edits")
				.with(httpBasic("admin", PASSWORD))
				.contentType(MediaType.APPLICATION_JSON)
				.content(EDIT))
				.andExpect(status().isOk());
	}

	static class ClockConfig {

		@Bean
		Clock clock() {
			return Clock.fixed(Instant.parse("2026-07-01T04:30:00Z"), ZoneOffset.UTC);
		}

	}

}
