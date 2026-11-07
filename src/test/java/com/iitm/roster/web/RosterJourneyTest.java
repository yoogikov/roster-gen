package com.iitm.roster.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.iitm.roster.domain.Assignment;
import com.iitm.roster.domain.DayOff;
import com.iitm.roster.domain.Post;
import com.iitm.roster.domain.Roster;
import com.iitm.roster.domain.StandardShifts;
import com.iitm.roster.export.CsvRosterExporter;
import com.iitm.roster.repo.SavedRule;
import com.iitm.roster.service.RosterQuery;
import com.iitm.roster.service.RuleBook;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * The CMO's path through the running app: save a rule, generate December, edit, swap,
 * export, then the audit log is exactly those writes (generate, edit, publish, two moves).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
@Import(RosterJourneyTest.FixedClockConfig.class)
@TestPropertySource(properties = "roster.dev-users.password=" + RosterJourneyTest.PASSWORD)
class RosterJourneyTest {

	static final String PASSWORD = "test-only-password";

	private static final YearMonth DECEMBER = YearMonth.of(2026, 12);
	private static final LocalDate TODAY = LocalDate.of(2026, 11, 7);

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ObjectMapper json;

	@Autowired
	private RuleBook rules;

	@Autowired
	private RosterQuery query;

	@Test
	@Timeout(value = 2, unit = TimeUnit.MINUTES)
	void ruleThenGenerateEditSwapExportWritesTheAuditSequence() throws Exception {
		mockMvc.perform(post("/rules")
				.with(httpBasic("admin", PASSWORD))
				.param("month", "2026-12")
				.param("templateId", "post-duty-off"))
				.andExpect(status().isOk());
		assertThat(rules.saved(DECEMBER)).extracting(SavedRule::templateId).contains("post-duty-off");

		String monthInput = new String(new ClassPathResource("journey-december.json").getInputStream().readAllBytes(),
				StandardCharsets.UTF_8);
		MvcResult generated = mockMvc.perform(post("/api/rosters/generate")
				.with(httpBasic("admin", PASSWORD))
				.contentType(MediaType.APPLICATION_JSON)
				.content(monthInput))
				.andExpect(status().isOk())
				.andReturn();
		assertThat(json.readTree(generated.getResponse().getContentAsString()).path("outcome").asString()).isEqualTo("ok");

		Roster roster = stored();
		Map<String, String> extra = extraGeneral(roster);
		MvcResult edited = mockMvc.perform(post("/api/rosters/2026-12/edits")
				.with(httpBasic("admin", PASSWORD))
				.contentType(MediaType.APPLICATION_JSON)
				.content(json.writeValueAsString(extra)))
				.andExpect(status().isOk())
				.andReturn();
		assertThat(json.readTree(edited.getResponse().getContentAsString()).path("outcome").asString()).isEqualTo("applied");

		mockMvc.perform(post("/api/rosters/2026-12/publish").with(httpBasic("admin", PASSWORD)))
				.andExpect(status().isOk());

		roster = stored();
		Map<String, String> swap = swapPair(roster);
		MvcResult proposed = mockMvc.perform(post("/api/swaps")
				.with(httpBasic("staff1", PASSWORD))
				.contentType(MediaType.APPLICATION_JSON)
				.content(json.writeValueAsString(swap)))
				.andExpect(status().isOk())
				.andReturn();
		String swapId = json.readTree(proposed.getResponse().getContentAsString()).path("id").asString();
		mockMvc.perform(post("/api/swaps/" + swapId + "/accept").with(httpBasic("staff2", PASSWORD)))
				.andExpect(status().isOk());
		MvcResult approved = mockMvc.perform(post("/api/swaps/" + swapId + "/approve").with(httpBasic("admin", PASSWORD)))
				.andExpect(status().isOk())
				.andReturn();
		assertThat(json.readTree(approved.getResponse().getContentAsString()).path("state").asString()).isEqualTo("APPROVED");

		MvcResult csv = mockMvc.perform(get("/api/rosters/2026-12/export").param("format", "csv")
				.with(httpBasic("admin", PASSWORD)))
				.andExpect(status().isOk())
				.andReturn();
		String body = csv.getResponse().getContentAsString();
		assertThat(body).startsWith(CsvRosterExporter.HEADER);
		assertThat(csvRow(body, swap.get("offeredDate"))).contains("Test Doctor 6");
		assertThat(csvRow(body, swap.get("wantedDate"))).contains("Test Doctor 1");

		MvcResult audit = mockMvc.perform(get("/api/audit").with(httpBasic("admin", PASSWORD)))
				.andExpect(status().isOk())
				.andReturn();
		List<String> summaries = new ArrayList<>();
		for (JsonNode event : json.readTree(audit.getResponse().getContentAsString())) {
			summaries.add(event.path("summary").asString());
		}
		assertThat(summaries).hasSize(5);
		assertThat(summaries.get(0)).startsWith("generated 2026-12: every shift filled, SOFT penalty ");
		assertThat(summaries.get(1)).isEqualTo("assigned " + extra.get("staffId") + " to II (GENERAL) on "
				+ extra.get("date"));
		assertThat(summaries.get(2)).isEqualTo("published 2026-12");
		assertThat(summaries.get(3)).isEqualTo("moved " + swap.get("offeredShift") + " ("
				+ swap.get("offeredPost") + ") on " + swap.get("offeredDate")
				+ " from D01 to D06");
		assertThat(summaries.get(4)).isEqualTo("moved " + swap.get("wantedShift") + " ("
				+ swap.get("wantedPost") + ") on " + swap.get("wantedDate")
				+ " from D06 to D01");
		assertThat(json.readTree(audit.getResponse().getContentAsString()).get(0).path("actor").asString())
				.isEqualTo("admin");
	}

	private Roster stored() {
		return query.find(DECEMBER).orElseThrow();
	}

	private static String csvRow(String csv, String date) {
		for (String line : csv.split("\n")) {
			if (line.startsWith(date + ",")) {
				return line;
			}
		}
		throw new AssertionError("no CSV row for " + date);
	}

	private static Map<String, String> extraGeneral(Roster roster) {
		Set<String> offs = offs(roster);
		Set<String> nights = nights(roster);
		Set<String> busy = occupied(roster);
		for (Assignment assignment : roster.assignments()) {
			if (!assignment.slot().shift().equals(StandardShifts.SHIFT_II)) {
				continue;
			}
			LocalDate day = assignment.slot().date();
			if (!day.isAfter(TODAY)) {
				continue;
			}
			String yesterday = day.minusDays(1).toString();
			for (String doctor : List.of("D07", "D08", "D09", "D10", "D11", "D02", "D03", "D04", "D05")) {
				if (!(busy.contains(doctor + ":" + day) || offs.contains(doctor + ":" + day)
						|| nights.contains(doctor + ":" + yesterday))) {
					return Map.of("type", "assign", "date", day.toString(), "shift", "II", "staffId", doctor, "post",
							"GENERAL");
				}
			}
		}
		throw new AssertionError("no free doctor to assign on Shift II");
	}

	private static Map<String, String> swapPair(Roster roster) {
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
					Map<String, String> body = new LinkedHashMap<>();
					body.put("offeredDate", left.slot().date().toString());
					body.put("offeredShift", left.slot().shift().code());
					body.put("offeredStaffId", "D01");
					body.put("offeredPost", left.post().name());
					body.put("wantedDate", right.slot().date().toString());
					body.put("wantedShift", right.slot().shift().code());
					body.put("wantedStaffId", "D06");
					body.put("wantedPost", right.post().name());
					return body;
				}
			}
		}
		throw new AssertionError("D01 and D06 have no safe day-shift pair; D01 movable " + lefts.size()
				+ ", D06 movable " + rights.size());
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

	static class FixedClockConfig {

		@Bean
		@Primary
		Clock clock() {
			return Clock.fixed(Instant.parse("2026-11-07T04:30:00Z"), ZoneOffset.UTC);
		}

	}

}
