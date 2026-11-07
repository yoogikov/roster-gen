package com.iitm.roster.security;

import com.iitm.roster.domain.Staff;
import com.iitm.roster.domain.StaffRole;
import com.iitm.roster.repo.StaffStore;
import com.iitm.roster.repo.UserAccount;
import com.iitm.roster.repo.UserAccountStore;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.env.Environment;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Ensures there is an admin login on start. Prod requires {@code ROSTER_ADMIN_PASSWORD}.
 * Dev may generate one and also seeds reception, D01/D06 and staff1/staff2 for the demo.
 */
@Component
@SuppressFBWarnings(value = "EI_EXPOSE_REP2",
		justification = "the stores are Spring singletons injected on purpose; sharing them is the point")
public class AccountBootstrap implements ApplicationRunner {

	private static final Logger log = LoggerFactory.getLogger(AccountBootstrap.class);

	private final UserAccountStore accounts;
	private final StaffStore staff;
	private final PasswordEncoder encoder;
	private final Environment environment;
	private final String adminPassword;
	private final String devPassword;

	public AccountBootstrap(UserAccountStore accounts, StaffStore staff, PasswordEncoder encoder,
			Environment environment,
			@Value("${roster.admin-password:}") String adminPassword,
			@Value("${roster.dev-users.password:}") String devPassword) {
		this.accounts = accounts;
		this.staff = staff;
		this.encoder = encoder;
		this.environment = environment;
		this.adminPassword = adminPassword;
		this.devPassword = devPassword;
	}

	@Override
	@Transactional
	public void run(ApplicationArguments args) {
		boolean prod = environment.matchesProfiles("prod");
		String password = resolvePassword(prod);
		if (!accounts.hasAdmin()) {
			accounts.save(new UserAccount("admin", encoder.encode(password), "ADMIN", null, true));
		}
		if (!prod) {
			seedDev(password);
		}
	}

	private String resolvePassword(boolean prod) {
		if (prod) {
			if (adminPassword == null || adminPassword.isBlank()) {
				throw new IllegalStateException("ROSTER_ADMIN_PASSWORD must be set in prod");
			}
			return adminPassword;
		}
		if (devPassword != null && !devPassword.isBlank()) {
			return devPassword;
		}
		if (adminPassword != null && !adminPassword.isBlank()) {
			return adminPassword;
		}
		String generated = UUID.randomUUID().toString();
		log.warn("ROSTER_DEV_PASSWORD not set; seeded logins share generated password: {}", generated);
		return generated;
	}

	private void seedDev(String password) {
		String encoded = encoder.encode(password);
		ensureStaff("D01", "Test Doctor 1");
		ensureStaff("D06", "Test Doctor 6");
		ensureAccount("reception", encoded, "RECEPTION", null);
		ensureAccount("staff1", encoded, "STAFF", "D01");
		ensureAccount("staff2", encoded, "STAFF", "D06");
	}

	private void ensureStaff(String id, String name) {
		if (staff.findById(id).isEmpty()) {
			staff.save(new Staff(id, name, StaffRole.DOCTOR));
		}
	}

	private void ensureAccount(String username, String encoded, String role, String staffId) {
		if (accounts.findByUsername(username).isEmpty()) {
			accounts.save(new UserAccount(username, encoded, role, staffId, true));
		}
	}

}
