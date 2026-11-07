package com.iitm.roster.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.iitm.roster.repo.StaffRecord;
import com.iitm.roster.repo.StaffStore;
import com.iitm.roster.repo.UserAccountStore;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

@SpringBootTest
@ActiveProfiles("dev")
@TestPropertySource(properties = "roster.dev-users.password=" + AccountServiceTest.PASSWORD)
class AccountServiceTest {

	static final String PASSWORD = "test-only-password";

	@Autowired
	private AccountService accounts;

	@Autowired
	private StaffStore staff;

	@Autowired
	private UserAccountStore users;

	@Autowired
	private PasswordEncoder encoder;

	@Test
	void addingADoctorStoresAnActiveRecord() {
		StaffRecord doctor = accounts.addDoctor("D20", "Test Doctor 20");

		assertThat(doctor.id()).isEqualTo("D20");
		assertThat(doctor.active()).isTrue();
		assertThat(staff.findActive()).extracting(com.iitm.roster.domain.Staff::id).contains("D20");
	}

	@Test
	void createStaffLoginReturnsAWorkingTemporaryPassword() {
		accounts.addDoctor("D21", "Test Doctor 21");

		String temporary = accounts.createStaffLogin("doc21", "D21");

		assertThat(temporary).isNotBlank();
		assertThat(users.findByUsername("doc21")).get().satisfies(account -> {
			assertThat(account.role()).isEqualTo("STAFF");
			assertThat(account.staffId()).isEqualTo("D21");
			assertThat(encoder.matches(temporary, account.passwordHash())).isTrue();
		});
	}

	@Test
	void cannotCreateASecondLoginForTheSameDoctor() {
		accounts.addDoctor("D22", "Test Doctor 22");
		accounts.createStaffLogin("doc22a", "D22");

		assertThatThrownBy(() -> accounts.createStaffLogin("doc22b", "D22"))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("already has a login");
	}

	@Test
	void changeOwnPasswordRejectsAWrongOldPassword() {
		accounts.addDoctor("D23", "Test Doctor 23");
		String temporary = accounts.createStaffLogin("doc23", "D23");

		assertThatThrownBy(() -> accounts.changeOwnPassword("doc23", "not-" + temporary, "new-password"))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("old password is wrong");
	}

	@Test
	void inactiveDoctorCannotGetALogin() {
		accounts.addDoctor("D24", "Test Doctor 24");
		accounts.setActive("D24", false);

		assertThatThrownBy(() -> accounts.createStaffLogin("doc24", "D24"))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("inactive");
	}

}
