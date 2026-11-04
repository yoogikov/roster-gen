package com.iitm.roster.security;

import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * Synthetic in-memory users, one or more per role, for the dev profile only.
 * They are not real hospital staff. All share one password, taken from
 * {@code ROSTER_DEV_PASSWORD}; if that is unset, a random one is generated and logged.
 * {@code staff1} is doctor {@code D01} and {@code staff2} is doctor {@code D06}, so a swap
 * in the demo can be offered and accepted. D02's month is too full to exchange a shift
 * with D01 without breaking a hospital rule. Admin and reception are not rostered.
 */
@Configuration
@Profile("dev")
public class DevUsersConfig {

	private static final Logger log = LoggerFactory.getLogger(DevUsersConfig.class);

	/**
	 * Not {@code InMemoryUserDetailsManager}: that wraps the user and drops the staff id,
	 * so a swap could no longer tell which doctor was signed in.
	 */
	@Bean
	UserDetailsService devUsers(
			@Value("${roster.dev-users.password:}") String configuredPassword,
			PasswordEncoder passwordEncoder) {
		String password = resolvePassword(configuredPassword);
		String encoded = passwordEncoder.encode(password);
		Map<String, RosterUser> users = Map.of(
				"admin", user("admin", encoded, "ADMIN", null),
				"reception", user("reception", encoded, "RECEPTION", null),
				"staff1", user("staff1", encoded, "STAFF", "D01"),
				"staff2", user("staff2", encoded, "STAFF", "D06"));
		// A fresh copy each time: the security filter erases the password on the
		// object it authenticated, and that must not wipe the stored user.
		return username -> {
			RosterUser found = users.get(username);
			if (found == null) {
				throw new UsernameNotFoundException(username);
			}
			return new RosterUser(found.getUsername(), found.getPassword(), found.role(), found.staffId().orElse(null));
		};
	}

	static String resolvePassword(String configuredPassword) {
		if (configuredPassword != null && !configuredPassword.isBlank()) {
			return configuredPassword;
		}
		String generated = UUID.randomUUID().toString();
		log.warn("ROSTER_DEV_PASSWORD not set; dev users share generated password: {}", generated);
		return generated;
	}

	private static RosterUser user(String username, String encodedPassword, String role, String staffId) {
		return new RosterUser(username, encodedPassword, role, staffId);
	}

}
