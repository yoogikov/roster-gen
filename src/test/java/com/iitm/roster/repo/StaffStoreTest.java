package com.iitm.roster.repo;

import static com.iitm.roster.repo.RepoFixtures.DOC_A;
import static com.iitm.roster.repo.RepoFixtures.DOC_B;
import static com.iitm.roster.repo.RepoFixtures.NURSE_C;
import static org.assertj.core.api.Assertions.assertThat;

import com.iitm.roster.domain.Staff;
import com.iitm.roster.domain.StaffRole;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;

@DataJpaTest
@Import(StaffStore.class)
class StaffStoreTest {

	@Autowired
	private StaffStore staff;

	@Autowired
	private TestEntityManager em;

	@Test
	void staffSurvivesSaveAndReload() {
		staff.save(NURSE_C);
		flushAndClear();

		assertThat(staff.findById("S03")).contains(NURSE_C);
	}

	@Test
	void findAllIsOrderedById() {
		staff.save(NURSE_C);
		staff.save(DOC_A);
		staff.save(DOC_B);
		flushAndClear();

		assertThat(staff.findAll()).containsExactly(DOC_A, DOC_B, NURSE_C);
	}

	@Test
	void savingAnExistingIdUpdatesItInsteadOfAddingAnother() {
		staff.save(DOC_A);
		Staff changed = new Staff(DOC_A.id(), "Test Doctor A Renamed", StaffRole.NURSE);

		staff.save(changed);
		flushAndClear();

		assertThat(staff.findAll()).containsExactly(changed);
	}

	@Test
	void unknownIdIsEmpty() {
		assertThat(staff.findById("S99")).isEmpty();
	}

	private void flushAndClear() {
		em.flush();
		em.clear();
	}

}
