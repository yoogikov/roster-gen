package com.iitm.roster.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.iitm.roster.domain.Assignment;
import com.iitm.roster.domain.ChangeRequest;
import com.iitm.roster.domain.DayOff;
import com.iitm.roster.domain.Post;
import com.iitm.roster.domain.RequestState;
import com.iitm.roster.domain.Roster;
import com.iitm.roster.domain.StandardShifts;
import com.iitm.roster.domain.SwapRequest;
import com.iitm.roster.repo.ChangeRequestStore;
import com.iitm.roster.repo.SwapRequestStore;
import com.iitm.roster.service.RosterQuery;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.ObjectMapper;

/**
 * Staff propose a swap and a change from the sheet; the inbox shows my requests;
 * offering someone else's shift is refused. Reception stays read-only.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
@Import(StaffSwapFormTest.FixedClockConfig.class)
@TestPropertySource(properties = "roster.dev-users.password=" + StaffSwapFormTest.PASSWORD)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class StaffSwapFormTest {

	static final String PASSWORD = "test-only-password";

	private static final YearMonth DECEMBER = YearMonth.of(2026, 12);
	private static final LocalDate TODAY = LocalDate.of(2026, 11, 7);

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ObjectMapper json;

	@Autowired
	private RosterQuery query;

	@Autowired
	private SwapRequestStore swaps;

	@Autowired
	private ChangeRequestStore changes;

	@Test
	@Timeout(value = 2, unit = TimeUnit.MINUTES)
	void staffProposeSwapFromTheSheetThenAcceptAndApprove() throws Exception {
		publishDecember();
		Assignment[] pair = safePair(stored());
		Assignment offered = pair[0];
		Assignment wanted = pair[1];

		String offeredValue = encode(offered);
		String wantedValue = encode(wanted);

		mockMvc.perform(get("/rosters/2026-12").with(httpBasic("staff1", PASSWORD)))
				.andExpect(status().isOk())
				.andExpect(content().string(org.hamcrest.Matchers.containsString("Propose a swap")))
				.andExpect(content().string(org.hamcrest.Matchers.containsString(offeredValue)));

		mockMvc.perform(post("/rosters/2026-12/swaps").with(httpBasic("staff1", PASSWORD))
				.param("offered", offeredValue)
				.param("wanted", wantedValue))
				.andExpect(status().isOk())
				.andExpect(content().string(org.hamcrest.Matchers.containsString("swap proposed")))
				.andExpect(content().string(org.hamcrest.Matchers.containsString("Test Doctor 1")));

		List<SwapRequest> open = swaps.findOpen();
		assertThat(open).hasSize(1);
		String swapId = open.get(0).id();

		mockMvc.perform(get("/inbox").with(httpBasic("staff1", PASSWORD)))
				.andExpect(status().isOk())
				.andExpect(content().string(org.hamcrest.Matchers.containsString("My requests")))
				.andExpect(content().string(org.hamcrest.Matchers.containsString("PENDING")));

		mockMvc.perform(post("/inbox/swaps/" + swapId + "/accept").with(httpBasic("staff2", PASSWORD)))
				.andExpect(status().isOk())
				.andExpect(content().string(org.hamcrest.Matchers.containsString("swap accepted")));

		mockMvc.perform(post("/inbox/swaps/" + swapId + "/approve").with(httpBasic("admin", PASSWORD)))
				.andExpect(status().isOk())
				.andExpect(content().string(org.hamcrest.Matchers.containsString("swap approved")));

		Roster after = stored();
		assertThat(after.assignmentsOn(offered.slot())).anyMatch(a -> "D06".equals(a.staff().id()));
		assertThat(after.assignmentsOn(wanted.slot())).anyMatch(a -> "D01".equals(a.staff().id()));
	}

	@Test
	@Timeout(value = 2, unit = TimeUnit.MINUTES)
	void staffChangeRequestAppearsForAdminApproval() throws Exception {
		publishDecember();
		Assignment mine = safePair(stored())[0];

		mockMvc.perform(post("/rosters/2026-12/edits").with(httpBasic("staff1", PASSWORD))
				.param("type", "unassign")
				.param("date", mine.slot().date().toString())
				.param("shift", mine.slot().shift().code())
				.param("staffId", "D01")
				.param("post", mine.post().name()))
				.andExpect(status().isOk())
				.andExpect(content().string(org.hamcrest.Matchers.containsString("ADMIN")))
				.andExpect(content().string(org.hamcrest.Matchers.containsString("stored for approval")));

		List<ChangeRequest> open = changes.findOpen();
		assertThat(open).isNotEmpty();
		assertThat(open.get(0).staffId()).isEqualTo("D01");
		assertThat(open.get(0).state()).isEqualTo(RequestState.PENDING);

		mockMvc.perform(get("/inbox").with(httpBasic("staff1", PASSWORD)))
				.andExpect(status().isOk())
				.andExpect(content().string(org.hamcrest.Matchers.containsString("unassign")))
				.andExpect(content().string(org.hamcrest.Matchers.containsString("PENDING")));
	}

	@Test
	@Timeout(value = 2, unit = TimeUnit.MINUTES)
	void staffCannotOfferSomeoneElsesShiftFromTheForm() throws Exception {
		publishDecember();
		Assignment[] pair = safePair(stored());
		Assignment others = pair[1];
		Assignment another = movable(stored(), "D02").get(0);

		mockMvc.perform(post("/rosters/2026-12/swaps").with(httpBasic("staff1", PASSWORD))
				.param("offered", encode(others))
				.param("wanted", encode(another)))
				.andExpect(status().isOk())
				.andExpect(content().string(org.hamcrest.Matchers.containsString("staff can only offer their own shift")));

		assertThat(swaps.findOpen()).isEmpty();
	}

	@Test
	@Timeout(value = 2, unit = TimeUnit.MINUTES)
	void receptionSeesNoRequestForms() throws Exception {
		publishDecember();

		mockMvc.perform(get("/rosters/2026-12").with(httpBasic("reception", PASSWORD)))
				.andExpect(status().isOk())
				.andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("Propose a swap"))))
				.andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("edit-form"))))
				.andExpect(content().string(org.hamcrest.Matchers.containsString("Read-only")));
	}

	private void publishDecember() throws Exception {
		mockMvc.perform(post("/rules").with(httpBasic("admin", PASSWORD))
				.param("month", "2026-12")
				.param("templateId", "post-duty-off"))
				.andExpect(status().isOk());

		String monthInput = new String(new ClassPathResource("journey-december.json").getInputStream().readAllBytes(),
				StandardCharsets.UTF_8);
		MvcResult generated = mockMvc.perform(post("/api/rosters/generate")
				.with(httpBasic("admin", PASSWORD))
				.contentType(MediaType.APPLICATION_JSON)
				.content(monthInput))
				.andExpect(status().isOk())
				.andReturn();
		assertThat(json.readTree(generated.getResponse().getContentAsString()).path("outcome").asString()).isEqualTo("ok");

		mockMvc.perform(post("/rosters/2026-12/publish").with(httpBasic("admin", PASSWORD)))
				.andExpect(status().isOk())
				.andExpect(content().string(org.hamcrest.Matchers.containsString("published")));
	}

	private Roster stored() {
		return query.find(DECEMBER).orElseThrow();
	}

	private static Assignment[] safePair(Roster roster) {
		Set<String> offs = offs(roster);
		Set<String> nights = nights(roster);
		Set<String> busy = occupied(roster);
		List<Assignment> lefts = movable(roster, "D01");
		List<Assignment> rights = movable(roster, "D06");
		for (Assignment left : lefts) {
			for (Assignment right : rights) {
				if (!left.slot().date().equals(right.slot().date())
						&& receiverFree("D06", left, offs, nights, busy)
						&& receiverFree("D01", right, offs, nights, busy)) {
					return new Assignment[] { left, right };
				}
			}
		}
		throw new AssertionError("D01 and D06 have no safe day-shift pair");
	}

	private static boolean receiverFree(String person, Assignment source, Set<String> offs, Set<String> nights,
			Set<String> busy) {
		String day = source.slot().date().toString();
		String yesterday = source.slot().date().minusDays(1).toString();
		return !offs.contains(person + ":" + day) && !busy.contains(person + ":" + day)
				&& !nights.contains(person + ":" + yesterday);
	}

	private static List<Assignment> movable(Roster roster, String staffId) {
		List<Assignment> found = new ArrayList<>();
		for (Assignment assignment : roster.assignments()) {
			if (!staffId.equals(assignment.staff().id())) {
				continue;
			}
			String code = assignment.slot().shift().code();
			LocalDate day = assignment.slot().date();
			if (!day.isAfter(TODAY) || (!"I".equals(code) && !"II".equals(code))) {
				continue;
			}
			if ("I".equals(code) && assignment.post() == Post.WARD) {
				continue;
			}
			found.add(assignment);
		}
		return found;
	}

	private static Set<String> offs(Roster roster) {
		Set<String> values = new HashSet<>();
		for (DayOff off : roster.weeklyOffs()) {
			values.add(off.staff().id() + ":" + off.date());
		}
		return values;
	}

	private static Set<String> nights(Roster roster) {
		Set<String> values = new HashSet<>();
		for (Assignment assignment : roster.assignments()) {
			if (assignment.slot().shift().equals(StandardShifts.NIGHT)) {
				values.add(assignment.staff().id() + ":" + assignment.slot().date());
			}
		}
		return values;
	}

	private static Set<String> occupied(Roster roster) {
		Set<String> values = new HashSet<>();
		for (Assignment assignment : roster.assignments()) {
			String code = assignment.slot().shift().code();
			if ("I".equals(code) || "II".equals(code)) {
				values.add(assignment.staff().id() + ":" + assignment.slot().date());
			}
		}
		return values;
	}

	private static String encode(Assignment assignment) {
		return assignment.slot().date() + "|" + assignment.slot().shift().code() + "|"
				+ assignment.staff().id() + "|" + assignment.post().name();
	}

	static class FixedClockConfig {

		@Bean
		@Primary
		Clock clock() {
			return Clock.fixed(Instant.parse("2026-11-07T04:30:00Z"), ZoneOffset.UTC);
		}

	}

}
