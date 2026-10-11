package com.iitm.roster.repo;

import static com.iitm.roster.repo.RepoFixtures.DOC_A;
import static com.iitm.roster.repo.RepoFixtures.DOC_B;
import static com.iitm.roster.repo.RepoFixtures.MORNING;
import static com.iitm.roster.repo.RepoFixtures.NIGHT;
import static com.iitm.roster.repo.RepoFixtures.on;
import static org.assertj.core.api.Assertions.assertThat;

import com.iitm.roster.domain.Assignment;
import com.iitm.roster.domain.Post;
import com.iitm.roster.domain.RequestState;
import com.iitm.roster.domain.ShiftSlot;
import com.iitm.roster.domain.SwapRequest;
import java.time.Instant;
import java.time.LocalDate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;

@DataJpaTest
@Import({SwapRequestStore.class, StaffStore.class})
class SwapRequestStoreTest {

	private static final LocalDate DAY = LocalDate.of(2026, 7, 10);
	private static final Instant T0 = Instant.parse("2026-07-01T04:30:00Z");

	@Autowired
	private SwapRequestStore requests;

	@Autowired
	private StaffStore staff;

	@Autowired
	private TestEntityManager em;

	@BeforeEach
	void saveStaff() {
		staff.save(DOC_A);
		staff.save(DOC_B);
	}

	@Test
	void requestSurvivesSaveAndReload() {
		SwapRequest request = swap("R1", RequestState.PENDING, T0);

		requests.save(request);
		flushAndClear();

		assertThat(requests.findById("R1")).contains(request);
	}

	@Test
	void savingAgainOverwritesTheState() {
		SwapRequest request = swap("R1", RequestState.PENDING, T0);
		requests.save(request);

		requests.save(request.withState(RequestState.APPROVED));
		flushAndClear();

		assertThat(requests.findById("R1")).contains(request.withState(RequestState.APPROVED));
	}

	@Test
	void findOpenReturnsOnlyPendingAndAcceptedOldestFirst() {
		SwapRequest accepted = swap("R1", RequestState.ACCEPTED, T0.plusSeconds(60));
		SwapRequest pending = swap("R2", RequestState.PENDING, T0);
		requests.save(accepted);
		requests.save(pending);
		requests.save(swap("R3", RequestState.APPROVED, T0));
		requests.save(swap("R4", RequestState.REJECTED, T0));
		requests.save(swap("R5", RequestState.LAPSED, T0));
		flushAndClear();

		assertThat(requests.findOpen()).containsExactly(pending, accepted);
	}

	@Test
	void postsOfBothAssignmentsSurviveSaveAndReload() {
		SwapRequest request = new SwapRequest("R1",
				new Assignment(new ShiftSlot(DAY, MORNING), DOC_A, Post.WARD),
				new Assignment(new ShiftSlot(DAY, NIGHT), DOC_B, Post.GENERAL),
				RequestState.PENDING, T0);

		requests.save(request);
		flushAndClear();

		assertThat(requests.findById("R1")).contains(request);
	}

	@Test
	void unknownIdIsEmpty() {
		assertThat(requests.findById("R99")).isEmpty();
	}

	private static SwapRequest swap(String id, RequestState state, Instant createdAt) {
		return new SwapRequest(id, on(DAY, MORNING, DOC_A), on(DAY, NIGHT, DOC_B), state, createdAt);
	}

	private void flushAndClear() {
		em.flush();
		em.clear();
	}

}
