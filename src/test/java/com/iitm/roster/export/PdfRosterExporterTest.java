package com.iitm.roster.export;

import static com.iitm.roster.domain.StandardShifts.NIGHT;
import static com.iitm.roster.domain.StandardShifts.SHIFT_I;
import static com.iitm.roster.domain.StandardShifts.SHIFT_II;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.iitm.roster.domain.Assignment;
import com.iitm.roster.domain.DayOff;
import com.iitm.roster.domain.Post;
import com.iitm.roster.domain.Roster;
import com.iitm.roster.domain.RosterStatus;
import com.iitm.roster.domain.ShiftSlot;
import com.iitm.roster.domain.ShiftType;
import com.iitm.roster.domain.Staff;
import com.iitm.roster.domain.StaffRole;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;

/** The printable roster: reads the PDF back with PDFBox and checks what a reader of the sheet would see. */
class PdfRosterExporterTest {

	private static final YearMonth OCTOBER = YearMonth.of(2026, 10);

	private final PdfRosterExporter exporter = new PdfRosterExporter(SHIFT_I, SHIFT_II, NIGHT);

	@Test
	void writesAnA4LandscapePdfTitledWithTheMonthAndStatus() throws IOException {
		byte[] pdf = exporter.export(Roster.empty(OCTOBER).withStatus(RosterStatus.PUBLISHED), Format.PDF);

		assertThat(new String(pdf, 0, 5, StandardCharsets.US_ASCII)).isEqualTo("%PDF-");
		try (PDDocument document = Loader.loadPDF(pdf)) {
			PDPage first = document.getPage(0);
			assertThat(first.getMediaBox().getWidth()).isGreaterThan(first.getMediaBox().getHeight());
			assertThat(text(document)).contains("Duty roster", "October 2026", "PUBLISHED");
		}
	}

	@Test
	void eachDayIsOneRowWithTheDoctorsInTheSheetsColumnOrder() throws IOException {
		Roster roster = Roster.empty(OCTOBER)
				.withAssignment(at(1, NIGHT, doctor(1), Post.WARD))
				.withAssignment(at(1, SHIFT_I, doctor(1), Post.WARD))
				.withAssignment(at(1, SHIFT_I, doctor(2), Post.CASUALTY))
				.withAssignment(at(1, SHIFT_I, doctor(3), Post.GENERAL))
				.withAssignment(at(1, SHIFT_II, doctor(4), Post.WARD))
				.withAssignment(at(1, SHIFT_II, doctor(5), Post.CASUALTY))
				.withAssignment(at(1, SHIFT_II, doctor(6), Post.GENERAL))
				.withWeeklyOffs(List.of(new DayOff(doctor(7), OCTOBER.atDay(1)), new DayOff(doctor(8), OCTOBER.atDay(2))));

		List<String> lines = lines(exporter.export(roster, Format.PDF));

		assertThat(lines).contains(
				"2026-10-01 Thu Test Doctor 1 Test Doctor 1 Test Doctor 2 Test Doctor 3 Test Doctor 4 Test Doctor 5 "
						+ "Test Doctor 6 Test Doctor 7",
				"2026-10-02 Fri Test Doctor 1 Test Doctor 8");
		assertThat(lines).anySatisfy(line -> assertThat(line).startsWith("2026-10-31 Sat"));
	}

	@Test
	void anEmptyMonthStillListsEveryDay() throws IOException {
		YearMonth february = YearMonth.of(2027, 2);

		List<String> lines = lines(exporter.export(Roster.empty(february), Format.PDF));

		assertThat(lines).filteredOn(line -> line.startsWith("2027-02-")).hasSize(28);
		assertThat(lines).contains("2027-02-28 Sun");
	}

	@Test
	void aCrowdedMonthRunsOntoMorePagesWithTheHeaderRepeatedAndNoNameLost() throws IOException {
		Roster roster = Roster.empty(OCTOBER);
		List<Staff> many = new ArrayList<>();
		for (int i = 1; i <= 12; i++) {
			many.add(doctor(i));
		}
		for (int day = 1; day <= 31; day++) {
			for (Staff doctor : many) {
				roster = roster.withAssignment(at(day, SHIFT_I, doctor, Post.GENERAL));
			}
		}

		try (PDDocument document = Loader.loadPDF(exporter.export(roster, Format.PDF))) {
			String text = text(document);
			int pages = document.getNumberOfPages();
			assertThat(pages).isGreaterThan(1);
			assertThat(text.split("Duty roster", -1)).as("title on every page").hasSize(pages + 1);
			assertThat(text.split("Casualty", -1)).as("both shifts' column headers on every page").hasSize(2 * pages + 1);
			assertThat(text).contains("Page " + pages + " of " + pages);
			for (int day = 1; day <= 31; day++) {
				assertThat(text).contains(OCTOBER.atDay(day).toString());
			}
			assertThat(text.split("Test Doctor 12", -1)).hasSize(31 + 1);
		}
	}

	@Test
	void aCharacterTheFontCannotDrawBecomesAQuestionMarkInsteadOfFailing() throws IOException {
		Staff unusual = new Staff("D99", "Test Doctor Łódź", StaffRole.DOCTOR);
		Roster roster = Roster.empty(OCTOBER).withAssignment(at(5, SHIFT_I, unusual, Post.WARD));

		List<String> lines = lines(exporter.export(roster, Format.PDF));

		assertThat(lines).anySatisfy(line -> assertThat(line).contains("Test Doctor ?ód?"));
	}

	@Test
	void aNameWiderThanItsColumnWrapsInsideTheCellInsteadOfRunningIntoTheNext() throws IOException {
		Staff longName = new Staff("D50", "Test Doctor Withaverylongsyntheticsurname Andasecondone", StaffRole.DOCTOR);
		Roster roster = Roster.empty(OCTOBER).withAssignment(at(5, SHIFT_I, longName, Post.WARD))
				.withAssignment(at(5, SHIFT_I, doctor(2), Post.CASUALTY));

		List<String> lines = lines(exporter.export(roster, Format.PDF));

		assertThat(lines).noneSatisfy(line -> assertThat(line).contains("Withaverylongsyntheticsurname Andasecondone"));
		assertThat(lines).contains("2026-10-05 Mon Test Doctor Test Doctor 2");
		assertThat(String.join("", lines)).contains("Andasecondone");
	}

	@Test
	void csvIsNotThisExportersFormat() {
		assertThatThrownBy(() -> exporter.export(Roster.empty(OCTOBER), Format.CSV))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("PDF only");
	}

	private static Staff doctor(int n) {
		return new Staff(String.format("D%02d", n), "Test Doctor " + n, StaffRole.DOCTOR);
	}

	private static Assignment at(int day, ShiftType shift, Staff staff, Post post) {
		return new Assignment(new ShiftSlot(OCTOBER.atDay(day), shift), staff, post);
	}

	private static String text(PDDocument document) throws IOException {
		PDFTextStripper stripper = new PDFTextStripper();
		stripper.setSortByPosition(true);
		return stripper.getText(document);
	}

	/** Each printed line, with runs of spaces collapsed to one. */
	private static List<String> lines(byte[] pdf) throws IOException {
		try (PDDocument document = Loader.loadPDF(pdf)) {
			return text(document).lines().map(line -> line.trim().replaceAll("\\s+", " ")).toList();
		}
	}

}
