package com.iitm.roster.export;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.iitm.roster.domain.Leave;
import com.iitm.roster.domain.Staff;
import com.iitm.roster.domain.StaffRole;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class CsvDoctorListReaderTest {

	private final CsvDoctorListReader reader = new CsvDoctorListReader();

	private DoctorList read(String text) throws IOException, InvalidDoctorListException {
		return reader.read(new ByteArrayInputStream(text.getBytes(StandardCharsets.UTF_8)));
	}

	private static Staff doctor(int n) {
		return new Staff(String.format("D%02d", n), "Test Doctor " + n, StaffRole.DOCTOR);
	}

	@Test
	void readsTheFixtureInRotationOrderWithItsLeave() throws Exception {
		DoctorList list;
		try (InputStream in = getClass().getResourceAsStream("/fixtures/doctors-october-2026.csv")) {
			list = reader.read(in);
		}

		assertThat(list.doctors()).hasSize(11).startsWith(doctor(1), doctor(2), doctor(3)).endsWith(doctor(11));
		assertThat(list.leaves()).containsExactly(
				new Leave(doctor(3), LocalDate.of(2026, 10, 7), LocalDate.of(2026, 10, 9)),
				new Leave(doctor(5), LocalDate.of(2026, 10, 22), LocalDate.of(2026, 11, 3)));
	}

	@Test
	void singleDaysAndSeveralRangesInOneCell() throws Exception {
		DoctorList list = read("id,name,leave\nD01,Test Doctor 1, 2026-10-15 ; 2026-10-20..2026-10-21\n");

		assertThat(list.leaves()).containsExactly(
				new Leave(doctor(1), LocalDate.of(2026, 10, 15), LocalDate.of(2026, 10, 15)),
				new Leave(doctor(1), LocalDate.of(2026, 10, 20), LocalDate.of(2026, 10, 21)));
	}

	@Test
	void skipsBlankAndCommentLinesAndAcceptsSpacesInTheHeader() throws Exception {
		DoctorList list = read("# made up\n\nid, name, leave\n\nD01,Test Doctor 1,\n# end\n");

		assertThat(list.doctors()).containsExactly(doctor(1));
		assertThat(list.leaves()).isEmpty();
	}

	@Test
	void aMissingHeaderIsRejected() {
		assertThatThrownBy(() -> read("D01,Test Doctor 1,\n"))
				.isInstanceOf(InvalidDoctorListException.class).hasMessage("line 1: the first row must be the header 'id,name,leave'");
	}

	@Test
	void aRowWithTheWrongNumberOfFieldsIsRejectedWithItsLine() {
		assertThatThrownBy(() -> read("id,name,leave\nD01,Test Doctor 1,\nD02,Doctor, Two,\n"))
				.isInstanceOf(InvalidDoctorListException.class).hasMessage("line 3: expected 3 fields (id, name, leave), found 4");
	}

	@Test
	void aBlankIdOrNameIsRejected() {
		assertThatThrownBy(() -> read("id,name,leave\n ,Test Doctor 1,\n"))
				.isInstanceOf(InvalidDoctorListException.class).hasMessage("line 2: id and name must not be blank");
		assertThatThrownBy(() -> read("id,name,leave\nD01, ,\n"))
				.isInstanceOf(InvalidDoctorListException.class).hasMessage("line 2: id and name must not be blank");
	}

	@Test
	void aDoctorListedTwiceIsRejected() {
		assertThatThrownBy(() -> read("id,name,leave\nD01,Test Doctor 1,\nD01,Test Doctor 1,\n"))
				.isInstanceOf(InvalidDoctorListException.class).hasMessage("line 3: doctor D01 is listed twice");
	}

	@Test
	void badLeaveIsRejected() {
		assertThatThrownBy(() -> read("id,name,leave\nD01,Test Doctor 1,7 Oct\n"))
				.isInstanceOf(InvalidDoctorListException.class).hasMessage("line 2: '7 Oct' is not a date like 2026-10-07");
		assertThatThrownBy(() -> read("id,name,leave\nD01,Test Doctor 1,2026-10-09..2026-10-07\n"))
				.isInstanceOf(InvalidDoctorListException.class).hasMessage("line 2: leave '2026-10-09..2026-10-07' ends before it starts");
		assertThatThrownBy(() -> read("id,name,leave\nD01,Test Doctor 1,2026-10-01..2026-10-02..2026-10-03\n"))
				.isInstanceOf(InvalidDoctorListException.class).hasMessageContaining("is not a day or a first..last range");
	}

	@Test
	void aListWithNoDoctorsIsRejected() {
		assertThatThrownBy(() -> read("id,name,leave\n"))
				.isInstanceOf(InvalidDoctorListException.class).hasMessage("the list has no doctors");
		assertThatThrownBy(() -> read(""))
				.isInstanceOf(InvalidDoctorListException.class).hasMessage("the list has no doctors");
	}

}
