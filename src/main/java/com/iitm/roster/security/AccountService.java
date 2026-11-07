package com.iitm.roster.security;

import com.iitm.roster.domain.Staff;
import com.iitm.roster.domain.StaffRole;
import com.iitm.roster.repo.StaffRecord;
import com.iitm.roster.repo.StaffStore;
import com.iitm.roster.repo.UserAccount;
import com.iitm.roster.repo.UserAccountStore;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.security.SecureRandom;
import java.util.HexFormat;
import java.util.Objects;
import java.util.Optional;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Doctor directory and logins. Temporary passwords are returned once to the admin and never
 * logged. Hashes go through {@link PasswordEncoder}.
 */
@Service
@SuppressFBWarnings(value = "EI_EXPOSE_REP2",
		justification = "the stores are Spring singletons injected on purpose; sharing them is the point")
public class AccountService {

	private static final SecureRandom RANDOM = new SecureRandom();

	private final StaffStore staff;
	private final UserAccountStore accounts;
	private final PasswordEncoder encoder;

	public AccountService(StaffStore staff, UserAccountStore accounts, PasswordEncoder encoder) {
		this.staff = staff;
		this.accounts = accounts;
		this.encoder = encoder;
	}

	@Transactional
	public StaffRecord addDoctor(String id, String name) {
		Objects.requireNonNull(id, "id");
		Objects.requireNonNull(name, "name");
		if (staff.findById(id).isPresent()) {
			throw new IllegalArgumentException("doctor " + id + " already exists");
		}
		Staff doctor = new Staff(id.trim(), name.trim(), StaffRole.DOCTOR);
		staff.save(doctor);
		return staff.findRecord(id).orElseThrow();
	}

	@Transactional
	public StaffRecord rename(String id, String name) {
		Staff existing = staff.findById(id).orElseThrow(() -> new IllegalArgumentException("unknown staff: " + id));
		staff.save(new Staff(existing.id(), name.trim(), existing.role()));
		return staff.findRecord(id).orElseThrow();
	}

	@Transactional
	public void setActive(String id, boolean active) {
		staff.setActive(id, active);
	}

	/**
	 * Creates a STAFF login for an active doctor. Returns the temporary password once.
	 *
	 * @throws IllegalArgumentException if the doctor is missing or inactive, the username is taken,
	 * or the doctor already has a login
	 */
	@Transactional
	public String createStaffLogin(String username, String staffId) {
		StaffRecord doctor = requireActiveDoctor(staffId);
		if (accounts.findByUsername(username).isPresent()) {
			throw new IllegalArgumentException("username " + username + " is already taken");
		}
		if (accounts.findByStaffId(staffId).isPresent()) {
			throw new IllegalArgumentException(staffId + " already has a login");
		}
		String temporary = temporaryPassword();
		accounts.save(new UserAccount(username.trim(), encoder.encode(temporary), "STAFF", doctor.id(), true));
		return temporary;
	}

	/** Resets a login's password. Returns the new temporary password once. */
	@Transactional
	public String resetPassword(String username) {
		UserAccount account = accounts.findByUsername(username)
				.orElseThrow(() -> new IllegalArgumentException("unknown user: " + username));
		String temporary = temporaryPassword();
		accounts.save(new UserAccount(account.username(), encoder.encode(temporary), account.role(),
				account.staffId(), account.enabled()));
		return temporary;
	}

	@Transactional
	public void setEnabled(String username, boolean enabled) {
		UserAccount account = accounts.findByUsername(username)
				.orElseThrow(() -> new IllegalArgumentException("unknown user: " + username));
		accounts.save(new UserAccount(account.username(), account.passwordHash(), account.role(),
				account.staffId(), enabled));
	}

	/**
	 * Changes the signed-in user's password. The old password must match.
	 *
	 * @throws IllegalArgumentException if the old password is wrong or the new one is blank
	 */
	@Transactional
	public void changeOwnPassword(String username, String oldPassword, String newPassword) {
		UserAccount account = accounts.findByUsername(username)
				.orElseThrow(() -> new IllegalArgumentException("unknown user: " + username));
		if (newPassword == null || newPassword.isBlank()) {
			throw new IllegalArgumentException("new password must not be blank");
		}
		if (!encoder.matches(oldPassword, account.passwordHash())) {
			throw new IllegalArgumentException("old password is wrong");
		}
		accounts.save(new UserAccount(account.username(), encoder.encode(newPassword), account.role(),
				account.staffId(), account.enabled()));
	}

	@Transactional(readOnly = true)
	public Optional<UserAccount> findLogin(String username) {
		return accounts.findByUsername(username);
	}

	@Transactional(readOnly = true)
	public Optional<UserAccount> findLoginForStaff(String staffId) {
		return accounts.findByStaffId(staffId);
	}

	private StaffRecord requireActiveDoctor(String staffId) {
		StaffRecord doctor = staff.findRecord(staffId)
				.orElseThrow(() -> new IllegalArgumentException("unknown staff: " + staffId));
		if (!doctor.active()) {
			throw new IllegalArgumentException(staffId + " is inactive");
		}
		return doctor;
	}

	/** 16 hex characters; never logged. */
	static String temporaryPassword() {
		byte[] bytes = new byte[8];
		RANDOM.nextBytes(bytes);
		return HexFormat.of().formatHex(bytes);
	}

}
