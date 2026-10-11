package com.iitm.roster.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = "roster.dev-users.password=" + SecurityConfigTest.PASSWORD)
@AutoConfigureMockMvc
@ActiveProfiles("dev")
class SecurityConfigTest {

	static final String PASSWORD = "test-only-password";

	@Autowired
	private MockMvc mockMvc;

	// No controllers exist yet, so an authenticated request reaches Spring MVC and gets 404.

	@Test
	void adminWithCorrectPasswordIsAuthenticated() throws Exception {
		mockMvc.perform(get("/api/ping").with(httpBasic("admin", PASSWORD)))
				.andExpect(status().isNotFound());
	}

	@Test
	void receptionWithCorrectPasswordIsAuthenticated() throws Exception {
		mockMvc.perform(get("/api/ping").with(httpBasic("reception", PASSWORD)))
				.andExpect(status().isNotFound());
	}

	@Test
	void staffWithCorrectPasswordIsAuthenticated() throws Exception {
		mockMvc.perform(get("/api/ping").with(httpBasic("staff1", PASSWORD)))
				.andExpect(status().isNotFound());
	}

	@Test
	void requestWithoutCredentialsIsChallengedForBasicAuth() throws Exception {
		mockMvc.perform(get("/api/ping"))
				.andExpect(status().isUnauthorized())
				.andExpect(header().string("WWW-Authenticate", startsWith("Basic")));
	}

	@Test
	void wrongPasswordIsRejected() throws Exception {
		mockMvc.perform(get("/api/ping").with(httpBasic("admin", "wrong")))
				.andExpect(status().isUnauthorized());
	}

	@Test
	void unknownUserIsRejected() throws Exception {
		mockMvc.perform(get("/api/ping").with(httpBasic("nobody", PASSWORD)))
				.andExpect(status().isUnauthorized());
	}

	@Test
	void authenticatedRequestDoesNotCreateSession() throws Exception {
		mockMvc.perform(get("/api/ping").with(httpBasic("admin", PASSWORD)))
				.andExpect(result -> assertThat(result.getRequest().getSession(false)).isNull());
	}

}
