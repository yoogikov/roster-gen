package com.iitm.roster.cli;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class GenerateRosterTest {

	@TempDir
	Path dir;

	private final ByteArrayOutputStream out = new ByteArrayOutputStream();
	private final ByteArrayOutputStream err = new ByteArrayOutputStream();

	private int run(String... args) {
		return new GenerateRoster().run(List.of(args),
				new PrintStream(out, true, StandardCharsets.UTF_8), new PrintStream(err, true, StandardCharsets.UTF_8));
	}

	private String err() {
		return err.toString(StandardCharsets.UTF_8);
	}

	private Path fixture() throws IOException {
		Path doctors = dir.resolve("doctors.csv");
		try (InputStream in = getClass().getResourceAsStream("/fixtures/doctors-october-2026.csv")) {
			Files.copy(in, doctors);
		}
		return doctors;
	}

	@Test
	void writesAFullOctoberToTheOutFileAndSaysSo() throws IOException {
		Path roster = dir.resolve("roster.csv");

		int code = run(fixture().toString(), "2026-10", "--holidays", "2026-10-02,2026-10-20", "--out", roster.toString());

		assertThat(code).isEqualTo(GenerateRoster.FILLED);
		assertThat(Files.readAllLines(roster)).hasSize(32);
		assertThat(out.size()).isZero();
		assertThat(err()).contains("2026-10: 11 doctors, 2 leave entries").contains("every shift filled")
				.doesNotContain("HARD");
	}

	@Test
	void tooFewDoctorsStillWritesTheRosterButListsTheGaps() throws IOException {
		Path doctors = dir.resolve("few.csv");
		Files.writeString(doctors, "id,name,leave\nD01,Test Doctor 1,\nD02,Test Doctor 2,\nD03,Test Doctor 3,\n");

		int code = run(doctors.toString(), "2026-10");

		assertThat(code).isEqualTo(GenerateRoster.UNFILLED);
		assertThat(out.toString(StandardCharsets.UTF_8).lines()).hasSize(32);
		assertThat(err()).contains("shifts could not be filled").contains("HARD min-staffing")
				.contains("2026-10-01 ").contains("needs at least 4 DOCTOR");
	}

	@Test
	void aBadDoctorListNamesTheFileAndLine() throws IOException {
		Path doctors = dir.resolve("bad.csv");
		Files.writeString(doctors, "id,name,leave\nD01,Test Doctor 1,7 Oct\n");

		assertThat(run(doctors.toString(), "2026-10")).isEqualTo(GenerateRoster.BAD_INPUT);
		assertThat(err()).contains("bad.csv: line 2: '7 Oct' is not a date");
	}

	@Test
	void aMissingFileIsBadInput() {
		assertThat(run(dir.resolve("missing.csv").toString(), "2026-10")).isEqualTo(GenerateRoster.BAD_INPUT);
		assertThat(err()).contains("cannot read");
	}

	@Test
	void badArgumentsPrintTheUsage() {
		assertThat(run("doctors.csv")).isEqualTo(GenerateRoster.BAD_INPUT);
		assertThat(err()).contains("expected a doctor list and a month").contains(GenerateRoster.USAGE);
	}

	@Test
	void argumentsAreParsedInAnyOrder() {
		GenerateRoster.Arguments args = GenerateRoster.Arguments.parse(
				List.of("--out", "r.csv", "doctors.csv", "--holidays", "2026-10-02, 2026-10-20", "2026-10"));

		assertThat(args.doctors()).isEqualTo(Path.of("doctors.csv"));
		assertThat(args.month()).isEqualTo(YearMonth.of(2026, 10));
		assertThat(args.holidays()).containsExactlyInAnyOrder(LocalDate.of(2026, 10, 2), LocalDate.of(2026, 10, 20));
		assertThat(args.out()).isEqualTo(Path.of("r.csv"));
	}

	@Test
	void badOptionsAreRejected() {
		assertThatThrownBy(() -> GenerateRoster.Arguments.parse(List.of("d.csv", "October")))
				.hasMessageContaining("is not a month like 2026-10");
		assertThatThrownBy(() -> GenerateRoster.Arguments.parse(List.of("d.csv", "2026-10", "--holidays", "2 Oct")))
				.hasMessageContaining("is not a date like 2026-10-02");
		assertThatThrownBy(() -> GenerateRoster.Arguments.parse(List.of("d.csv", "2026-10", "--out")))
				.hasMessage("--out needs a value");
		assertThatThrownBy(() -> GenerateRoster.Arguments.parse(List.of("d.csv", "2026-10", "--verbose")))
				.hasMessage("unknown option --verbose");
	}

}
