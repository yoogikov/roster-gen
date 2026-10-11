package com.iitm.roster.export;

import com.iitm.roster.domain.Leave;
import com.iitm.roster.domain.Staff;
import com.iitm.roster.domain.StaffRole;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Reads the doctor list, the app's only input: one doctor per row, in night-rotation order.
 *
 * <pre>
 * id,name,leave
 * D01,Test Doctor 1,
 * D02,Test Doctor 2,2026-10-07..2026-10-09
 * D03,Test Doctor 3,2026-10-15; 2026-10-22..2026-11-03
 * </pre>
 *
 * <p>Leave is a {@code ;}-separated list of single days or {@code first..last} ranges, both
 * days included; it may run past the month. Blank lines and lines starting with {@code #} are
 * skipped. Fields are not quoted, so a name cannot contain a comma.
 */
public final class CsvDoctorListReader {

	static final String HEADER = "id,name,leave";

	public DoctorList read(InputStream in) throws IOException, InvalidDoctorListException {
		BufferedReader lines = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
		List<Staff> doctors = new ArrayList<>();
		List<Leave> leaves = new ArrayList<>();
		Set<String> ids = new HashSet<>();
		boolean headerSeen = false;
		int number = 0;
		for (String line = lines.readLine(); line != null; line = lines.readLine()) {
			number++;
			String trimmed = line.strip();
			if (trimmed.isEmpty() || trimmed.startsWith("#")) {
				continue;
			}
			if (!headerSeen) {
				if (!HEADER.equals(trimmed.replace(" ", ""))) {
					throw invalid(number, "the first row must be the header '" + HEADER + "'");
				}
				headerSeen = true;
				continue;
			}
			String[] fields = trimmed.split(",", -1);
			if (fields.length != 3) {
				throw invalid(number, "expected 3 fields (id, name, leave), found " + fields.length);
			}
			String id = fields[0].strip();
			String name = fields[1].strip();
			if (id.isEmpty() || name.isEmpty()) {
				throw invalid(number, "id and name must not be blank");
			}
			if (!ids.add(id)) {
				throw invalid(number, "doctor " + id + " is listed twice");
			}
			Staff doctor = new Staff(id, name, StaffRole.DOCTOR);
			doctors.add(doctor);
			leaves.addAll(leave(doctor, fields[2], number));
		}
		if (doctors.isEmpty()) {
			throw new InvalidDoctorListException("the list has no doctors");
		}
		return new DoctorList(doctors, leaves);
	}

	private static List<Leave> leave(Staff doctor, String field, int number) throws InvalidDoctorListException {
		List<Leave> leaves = new ArrayList<>();
		for (String part : field.split(";", -1)) {
			String range = part.strip();
			if (range.isEmpty()) {
				continue;
			}
			String[] ends = range.split("\\.\\.", -1);
			if (ends.length > 2) {
				throw invalid(number, "leave '" + range + "' is not a day or a first..last range");
			}
			LocalDate first = date(ends[0], number);
			LocalDate last = ends.length == 2 ? date(ends[1], number) : first;
			if (last.isBefore(first)) {
				throw invalid(number, "leave '" + range + "' ends before it starts");
			}
			leaves.add(new Leave(doctor, first, last));
		}
		return leaves;
	}

	private static LocalDate date(String text, int number) throws InvalidDoctorListException {
		try {
			return LocalDate.parse(text.strip());
		} catch (DateTimeParseException e) {
			throw new InvalidDoctorListException(
					"line " + number + ": '" + text.strip() + "' is not a date like 2026-10-07", e);
		}
	}

	private static InvalidDoctorListException invalid(int number, String problem) {
		return new InvalidDoctorListException("line " + number + ": " + problem);
	}

}
