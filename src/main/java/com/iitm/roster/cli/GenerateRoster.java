package com.iitm.roster.cli;

import static com.iitm.roster.domain.StandardShifts.NIGHT;
import static com.iitm.roster.domain.StandardShifts.SHIFT_I;
import static com.iitm.roster.domain.StandardShifts.SHIFT_II;

import com.iitm.roster.constraints.Constraint;
import com.iitm.roster.constraints.Severity;
import com.iitm.roster.constraints.Violation;
import com.iitm.roster.domain.HospitalCalendar;
import com.iitm.roster.domain.Roster;
import com.iitm.roster.domain.ShiftSlot;
import com.iitm.roster.export.CsvDoctorListReader;
import com.iitm.roster.export.CsvRosterExporter;
import com.iitm.roster.export.DoctorList;
import com.iitm.roster.export.Format;
import com.iitm.roster.export.InvalidDoctorListException;
import com.iitm.roster.scheduler.GenerationResult;
import com.iitm.roster.scheduler.HospitalRosterGenerator;
import com.iitm.roster.scheduler.MonthInput;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Generates a month's roster from a doctor list:
 *
 * <pre>
 * ./gradlew generate --args="doctors.csv 2026-10 --holidays 2026-10-02,2026-10-20 --out roster.csv"
 * </pre>
 *
 * <p>Without {@code --out} the roster CSV goes to standard output. The summary (filled or not,
 * unfilled shifts, broken rules) always goes to standard error, so it never mixes with the CSV.
 * Exit code 0 when every shift is filled, 1 when some are left unfilled (the partial roster is
 * still written), 2 for bad arguments or a bad doctor list.
 */
public final class GenerateRoster {

	static final int FILLED = 0;
	static final int UNFILLED = 1;
	static final int BAD_INPUT = 2;

	static final String USAGE = "usage: generate <doctors.csv> <yyyy-MM> [--holidays yyyy-MM-dd,...] [--out roster.csv]";

	private final HospitalRosterGenerator generator = new HospitalRosterGenerator(SHIFT_I, SHIFT_II, NIGHT);
	private final CsvRosterExporter exporter = new CsvRosterExporter(SHIFT_I, SHIFT_II, NIGHT);

	public static void main(String[] args) {
		int code = new GenerateRoster().run(List.of(args), System.out, System.err);
		if (code != FILLED) {
			System.exit(code);
		}
	}

	/** Runs one generation; returns the exit code. */
	int run(List<String> args, PrintStream out, PrintStream err) {
		Arguments parsed;
		DoctorList doctors;
		try {
			parsed = Arguments.parse(args);
			try (InputStream in = Files.newInputStream(parsed.doctors())) {
				doctors = new CsvDoctorListReader().read(in);
			}
		} catch (IllegalArgumentException e) {
			err.println(e.getMessage());
			err.println(USAGE);
			return BAD_INPUT;
		} catch (InvalidDoctorListException e) {
			err.println(parsed(args) + ": " + e.getMessage());
			return BAD_INPUT;
		} catch (IOException e) {
			err.println("cannot read " + parsed(args) + ": " + e.getMessage());
			return BAD_INPUT;
		}

		MonthInput input = MonthInput.standard(parsed.month(), doctors.doctors(), doctors.leaves(),
				new HospitalCalendar(parsed.holidays()));
		GenerationResult result = generator.generate(input);
		Roster roster = switch (result) {
			case GenerationResult.Ok ok -> ok.roster();
			case GenerationResult.Infeasible infeasible -> infeasible.partial();
		};
		try {
			write(roster, parsed.out(), out);
		} catch (IOException e) {
			err.println("cannot write " + parsed.out() + ": " + e.getMessage());
			return BAD_INPUT;
		}
		return summarise(input, result, roster, err);
	}

	private static String parsed(List<String> args) {
		return args.isEmpty() ? "the doctor list" : args.get(0);
	}

	private void write(Roster roster, Path to, PrintStream out) throws IOException {
		byte[] csv = exporter.export(roster, Format.CSV);
		if (to == null) {
			out.write(csv);
			out.flush();
		} else {
			Files.write(to, csv);
		}
	}

	private int summarise(MonthInput input, GenerationResult result, Roster roster, PrintStream err) {
		err.println(input.month() + ": " + input.doctors().size() + " doctors, " + input.leaves().size() + " leave entries");
		int code = FILLED;
		switch (result) {
			case GenerationResult.Ok ok -> err.println("every shift filled; SOFT penalty " + ok.softPenalty());
			case GenerationResult.Infeasible infeasible -> {
				err.println(infeasible.unfilled().size() + " shifts could not be filled:");
				for (ShiftSlot slot : infeasible.unfilled()) {
					err.println("  " + slot.date() + " shift " + slot.shift().code());
				}
				code = UNFILLED;
			}
		}
		for (Constraint constraint : generator.constraints(input)) {
			List<Violation> broken = constraint.check(roster);
			if (broken.isEmpty()) {
				continue;
			}
			err.println(constraint.severity() + " " + constraint.id() + ": " + broken.size() + " broken");
			if (constraint.severity() == Severity.HARD) {
				broken.forEach(v -> err.println("  " + v.date() + " " + v.message()));
			}
		}
		return code;
	}

	/** The parsed command line. {@code out} is null for standard output. */
	record Arguments(Path doctors, YearMonth month, Set<LocalDate> holidays, Path out) {

		static Arguments parse(List<String> args) {
			List<String> positional = new ArrayList<>();
			Set<LocalDate> holidays = new HashSet<>();
			Path out = null;
			int i = 0;
			while (i < args.size()) {
				String arg = args.get(i);
				switch (arg) {
					case "--holidays" -> holidays.addAll(dates(value(args, i + 1, arg)));
					case "--out" -> out = Path.of(value(args, i + 1, arg));
					default -> {
						if (arg.startsWith("--")) {
							throw new IllegalArgumentException("unknown option " + arg);
						}
						positional.add(arg);
					}
				}
				i += arg.startsWith("--") ? 2 : 1;
			}
			if (positional.size() != 2) {
				throw new IllegalArgumentException("expected a doctor list and a month, got " + positional.size() + " arguments");
			}
			YearMonth month;
			try {
				month = YearMonth.parse(positional.get(1));
			} catch (DateTimeParseException e) {
				throw new IllegalArgumentException("'" + positional.get(1) + "' is not a month like 2026-10", e);
			}
			return new Arguments(Path.of(positional.get(0)), month, holidays, out);
		}

		private static String value(List<String> args, int index, String option) {
			if (index >= args.size()) {
				throw new IllegalArgumentException(option + " needs a value");
			}
			return args.get(index);
		}

		private static List<LocalDate> dates(String list) {
			List<LocalDate> dates = new ArrayList<>();
			for (String part : list.split(",")) {
				try {
					dates.add(LocalDate.parse(part.strip()));
				} catch (DateTimeParseException e) {
					throw new IllegalArgumentException("'" + part.strip() + "' is not a date like 2026-10-02", e);
				}
			}
			return dates;
		}

	}

}
