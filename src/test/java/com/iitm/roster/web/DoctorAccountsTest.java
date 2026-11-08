package com.iitm.roster.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.iitm.roster.domain.LeaveRequest;
import com.iitm.roster.domain.RequestState;
import com.iitm.roster.repo.LeaveRequestStore;
import com.iitm.roster.repo.UserAccountStore;
import com.iitm.roster.security.AccountService;
import java.time.LocalDate;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * Doctor directory and per-doctor logins: leave before any month, create login, 401/403,
 * and change-password with the erase-password copy behaviour.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
@TestPropertySource(properties = "roster.dev-users.password=" + DoctorAccountsTest.PASSWORD)
class DoctorAccountsTest {

	static final String PASSWORD = "test-only-password";

	private static final Pattern TEMP_PASSWORD = Pattern.compile("temporary password: ([0-9a-f]{16})");

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private AccountService accounts;

	@Autowired
	private LeaveRequestStore leaves;

	@Autowired
	private UserAccountStore users;

	@Autowired
	private PasswordEncoder encoder;

	@Test
	void doctorAddedOnDoctorsCanRequestLeaveBeforeAnyMonth() throws Exception {
		mockMvc.perform(post("/doctors").with(httpBasic("admin", PASSWORD))
				.param("id", "D30").param("name", "Test Doctor 30"))
				.andExpect(status().isOk());

		String temporary = createLoginShownOnce("D30", "doc30");

		mockMvc.perform(post("/leave").with(httpBasic("doc30", temporary))
				.param("staffId", "D30")
				.param("firstDay", "2026-12-10")
				.param("lastDay", "2026-12-12"))
				.andExpect(status().isOk())
				.andExpect(content().string(org.hamcrest.Matchers.containsString("leave requested")));

		List<LeaveRequest> open = leaves.findOpen();
		assertThat(open).anySatisfy(request -> {
			assertThat(request.staff().id()).isEqualTo("D30");
			assertThat(request.firstDay()).isEqualTo(LocalDate.of(2026, 12, 10));
			assertThat(request.state()).isEqualTo(RequestState.PENDING);
		});
	}

	@Test
	void adminCreatesLoginForD03WhoCannotRequestLeaveForD04() throws Exception {
		mockMvc.perform(post("/doctors").with(httpBasic("admin", PASSWORD))
				.param("id", "D03").param("name", "Test Doctor 3"))
				.andExpect(status().isOk());
		mockMvc.perform(post("/doctors").with(httpBasic("admin", PASSWORD))
				.param("id", "D04").param("name", "Test Doctor 4"))
				.andExpect(status().isOk());

		String temporary = createLoginShownOnce("D03", "doc03");

		mockMvc.perform(get("/api/ping").with(httpBasic("doc03", temporary)))
				.andExpect(status().isNotFound());

		assertThat(users.findByUsername("doc03")).get().satisfies(account -> {
			assertThat(account.role()).isEqualTo("STAFF");
			assertThat(account.staffId()).isEqualTo("D03");
		});

		mockMvc.perform(post("/leave").with(httpBasic("doc03", temporary))
				.param("staffId", "D04")
				.param("firstDay", "2026-12-01")
				.param("lastDay", "2026-12-02"))
				.andExpect(status().isOk())
				.andExpect(content().string(org.hamcrest.Matchers.anyOf(
						org.hamcrest.Matchers.containsString("Deny"),
						org.hamcrest.Matchers.containsString("not allowed"),
						org.hamcrest.Matchers.containsString("own"))));
	}

	@Test
	void wrongPasswordAndDisabledAccountGet401() throws Exception {
		mockMvc.perform(post("/doctors").with(httpBasic("admin", PASSWORD))
				.param("id", "D31").param("name", "Test Doctor 31"))
				.andExpect(status().isOk());
		String temporary = createLoginShownOnce("D31", "doc31");

		mockMvc.perform(get("/api/ping").with(httpBasic("doc31", "wrong-" + temporary)))
				.andExpect(status().isUnauthorized());

		mockMvc.perform(post("/doctors/logins/doc31/enabled").with(httpBasic("admin", PASSWORD))
				.param("enabled", "false"))
				.andExpect(status().isOk());

		mockMvc.perform(get("/api/ping").with(httpBasic("doc31", temporary)))
				.andExpect(status().isUnauthorized());
	}

	@Test
	void changingOwnPasswordWorksTwiceInARow() throws Exception {
		mockMvc.perform(post("/doctors").with(httpBasic("admin", PASSWORD))
				.param("id", "D32").param("name", "Test Doctor 32"))
				.andExpect(status().isOk());
		String temporary = createLoginShownOnce("D32", "doc32");

		mockMvc.perform(post("/password").with(httpBasic("doc32", temporary))
				.param("oldPassword", temporary)
				.param("newPassword", "changed-once"))
				.andExpect(status().isOk())
				.andExpect(content().string(org.hamcrest.Matchers.containsString("password changed")));

		mockMvc.perform(get("/api/ping").with(httpBasic("doc32", temporary)))
				.andExpect(status().isUnauthorized());
		mockMvc.perform(get("/api/ping").with(httpBasic("doc32", "changed-once")))
				.andExpect(status().isNotFound());
		mockMvc.perform(get("/api/ping").with(httpBasic("doc32", "changed-once")))
				.andExpect(status().isNotFound());

		assertThat(encoder.matches("changed-once", users.findByUsername("doc32").orElseThrow().passwordHash()))
				.isTrue();
	}

	@Test
	void staffAndReceptionCannotOpenDoctorsOrCreateLogins() throws Exception {
		mockMvc.perform(get("/doctors").with(httpBasic("staff1", PASSWORD)))
				.andExpect(status().isForbidden())
				.andExpect(content().string(org.hamcrest.Matchers.containsString("only an admin")));

		mockMvc.perform(post("/doctors").with(httpBasic("reception", PASSWORD))
				.param("id", "D99").param("name", "Test Doctor 99"))
				.andExpect(status().isForbidden())
				.andExpect(content().string(org.hamcrest.Matchers.containsString("only an admin")));

		mockMvc.perform(get("/doctors").with(httpBasic("admin", PASSWORD)))
				.andExpect(status().isOk())
				.andExpect(content().string(org.hamcrest.Matchers.containsString("Add a doctor")));
	}

	private String createLoginShownOnce(String staffId, String username) throws Exception {
		MvcResult result = mockMvc.perform(post("/doctors/" + staffId + "/login")
				.with(httpBasic("admin", PASSWORD))
				.param("username", username))
				.andExpect(status().isOk())
				.andReturn();
		String body = result.getResponse().getContentAsString();
		Matcher matcher = TEMP_PASSWORD.matcher(body);
		assertThat(matcher.find()).as("temporary password in notice: %s", body).isTrue();
		return matcher.group(1);
	}

}
