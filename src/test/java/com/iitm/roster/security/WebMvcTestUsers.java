package com.iitm.roster.security;

import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * In-memory users for {@code @WebMvcTest} slices that do not start JPA. Same four logins as the
 * old {@code DevUsersConfig}: admin, reception, staff1 (D01), staff2 (D06).
 */
@TestConfiguration
public class WebMvcTestUsers {

	@Bean
	UserDetailsService webMvcUsers(
			@Value("${roster.dev-users.password:test-only-password}") String password,
			PasswordEncoder passwordEncoder) {
		String encoded = passwordEncoder.encode(password);
		Map<String, RosterUser> users = Map.of(
				"admin", new RosterUser("admin", encoded, "ADMIN", null),
				"reception", new RosterUser("reception", encoded, "RECEPTION", null),
				"staff1", new RosterUser("staff1", encoded, "STAFF", "D01"),
				"staff2", new RosterUser("staff2", encoded, "STAFF", "D06"));
		return username -> {
			RosterUser found = users.get(username);
			if (found == null) {
				throw new UsernameNotFoundException(username);
			}
			return new RosterUser(found.getUsername(), found.getPassword(), found.isEnabled(), found.role(),
					found.staffId().orElse(null));
		};
	}

}
