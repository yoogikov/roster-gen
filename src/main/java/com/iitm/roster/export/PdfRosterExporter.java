package com.iitm.roster.export;

import com.iitm.roster.domain.Assignment;
import com.iitm.roster.domain.DayOff;
import com.iitm.roster.domain.Post;
import com.iitm.roster.domain.Roster;
import com.iitm.roster.domain.ShiftSlot;
import com.iitm.roster.domain.ShiftType;
import com.iitm.roster.domain.Staff;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;

/**
 * The printable roster: A4 landscape, one row per day, in the same columns as the CSV sheet
 * (night; Shift I and Shift II, each ward, casualty, general; post-duty off; weekly off).
 *
 * <p>Each name gets its own line inside a cell, so a crowded day makes a taller row rather than
 * a name cut in two. Rows that do not fit go onto the next page, which repeats the title and the
 * column headers. Weekends are shaded; holidays are not, because a {@link Roster} does not know
 * them. The font is the PDF built-in Helvetica, so no font file ships with the app; a character it
 * cannot draw is printed as {@code ?} rather than failing the export.
 */
public final class PdfRosterExporter implements RosterExporter {

	private static final PDRectangle PAGE = new PDRectangle(PDRectangle.A4.getHeight(), PDRectangle.A4.getWidth());
	private static final float MARGIN = 28;
	private static final float SIZE = 8;
	private static final float LINE = 10;
	private static final float PAD = 3;
	private static final float TITLE_SIZE = 12;
	private static final float TITLE_HEIGHT = 24;
	private static final float HEADER_ROW = LINE + 2 * PAD;
	/** Date, day, night, Shift I ward/casualty/general, Shift II ward/casualty/general, post-duty off; weekly off takes the rest. */
	private static final float[] FIXED_WIDTHS = {56, 28, 72, 72, 72, 84, 72, 72, 84, 72};
	private static final float GREY_LINE = 0.6f;
	private static final float WEEKEND_SHADE = 0.92f;

	private final ShiftType morning;
	private final ShiftType afternoon;
	private final ShiftType night;

	public PdfRosterExporter(ShiftType morning, ShiftType afternoon, ShiftType night) {
		this.morning = Objects.requireNonNull(morning, "morning");
		this.afternoon = Objects.requireNonNull(afternoon, "afternoon");
		this.night = Objects.requireNonNull(night, "night");
	}

	@Override
	public byte[] export(Roster roster, Format format) {
		if (format != Format.PDF) {
			throw new IllegalArgumentException("this exporter writes PDF only, not " + format);
		}
		try (PDDocument document = new PDDocument()) {
			Fonts fonts = new Fonts(new PDType1Font(Standard14Fonts.FontName.HELVETICA),
					new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD));
			float[] widths = columnWidths();
			List<Row> rows = new ArrayList<>();
			for (LocalDate day : roster.days()) {
				rows.add(row(roster, day, widths, fonts.regular()));
			}
			List<List<Row>> pages = paginate(rows);
			String title = "Duty roster - " + roster.month().getMonth().getDisplayName(TextStyle.FULL, Locale.ENGLISH)
					+ " " + roster.month().getYear() + " (" + roster.status().name() + ")";
			for (int i = 0; i < pages.size(); i++) {
				drawPage(document, pages.get(i), title, "Page " + (i + 1) + " of " + pages.size(), widths, fonts);
			}
			ByteArrayOutputStream out = new ByteArrayOutputStream();
			document.save(out);
			return out.toByteArray();
		} catch (IOException ex) {
			throw new UncheckedIOException("could not write the roster PDF", ex);
		}
	}

	private record Fonts(PDFont regular, PDFont bold) {
	}

	/** One day: the text lines of each cell, already wrapped to the cell's width. */
	private record Row(LocalDate day, List<List<String>> cells, float height) {
	}

	private static float[] columnWidths() {
		float[] widths = new float[FIXED_WIDTHS.length + 1];
		float used = 0;
		for (int i = 0; i < FIXED_WIDTHS.length; i++) {
			widths[i] = FIXED_WIDTHS[i];
			used += FIXED_WIDTHS[i];
		}
		widths[FIXED_WIDTHS.length] = PAGE.getWidth() - 2 * MARGIN - used;
		return widths;
	}

	private Row row(Roster roster, LocalDate day, float[] widths, PDFont font) throws IOException {
		List<List<String>> names = new ArrayList<>();
		names.add(List.of(day.toString()));
		names.add(List.of(day.getDayOfWeek().getDisplayName(TextStyle.SHORT, Locale.ENGLISH)));
		names.add(staffNames(roster.assignmentsOn(new ShiftSlot(day, night))));
		for (ShiftType shift : List.of(morning, afternoon)) {
			List<Assignment> on = roster.assignmentsOn(new ShiftSlot(day, shift));
			for (Post post : List.of(Post.WARD, Post.CASUALTY, Post.GENERAL)) {
				names.add(staffNames(on.stream().filter(a -> a.post() == post).toList()));
			}
		}
		names.add(staffNames(roster.assignmentsOn(new ShiftSlot(day.minusDays(1), night))));
		names.add(roster.weeklyOffs().stream().filter(off -> off.date().equals(day))
				.map(DayOff::staff).map(Staff::name).toList());
		List<List<String>> cells = new ArrayList<>();
		int tallest = 1;
		for (int i = 0; i < names.size(); i++) {
			List<String> lines = new ArrayList<>();
			for (String name : names.get(i)) {
				lines.addAll(wrap(printable(name, font), widths[i] - 2 * PAD, font));
			}
			cells.add(lines);
			tallest = Math.max(tallest, lines.size());
		}
		return new Row(day, cells, tallest * LINE + 2 * PAD);
	}

	private static List<String> staffNames(List<Assignment> assignments) {
		return assignments.stream().map(a -> a.staff().name()).toList();
	}

	/** Pages of rows, each page holding as many whole rows as fit under the title and headers. */
	private static List<List<Row>> paginate(List<Row> rows) {
		float room = PAGE.getHeight() - 2 * MARGIN - TITLE_HEIGHT - 2 * HEADER_ROW;
		List<List<Row>> pages = new ArrayList<>();
		List<Row> current = new ArrayList<>();
		float used = 0;
		for (Row row : rows) {
			if (!current.isEmpty() && used + row.height() > room) {
				pages.add(current);
				current = new ArrayList<>();
				used = 0;
			}
			current.add(row);
			used += row.height();
		}
		pages.add(current);
		return pages;
	}

	private void drawPage(PDDocument document, List<Row> rows, String title, String pageLabel, float[] widths,
			Fonts fonts) throws IOException {
		PDPage page = new PDPage(PAGE);
		document.addPage(page);
		try (PDPageContentStream out = new PDPageContentStream(document, page)) {
			float y = PAGE.getHeight() - MARGIN;
			text(out, fonts.bold(), TITLE_SIZE, MARGIN, y - TITLE_SIZE, title);
			float labelWidth = fonts.regular().getStringWidth(pageLabel) / 1000 * SIZE;
			text(out, fonts.regular(), SIZE, PAGE.getWidth() - MARGIN - labelWidth, y - TITLE_SIZE, pageLabel);
			y -= TITLE_HEIGHT;
			y = drawHeaders(out, y, widths, fonts.bold());
			// Shading first, for the whole page: a row's shading drawn after the row above
			// would paint over that row's bottom rule.
			float shadeTop = y;
			for (Row row : rows) {
				if (isWeekend(row.day())) {
					out.setNonStrokingColor(WEEKEND_SHADE, WEEKEND_SHADE, WEEKEND_SHADE);
					out.addRect(MARGIN, shadeTop - row.height(), PAGE.getWidth() - 2 * MARGIN, row.height());
					out.fill();
				}
				shadeTop -= row.height();
			}
			out.setNonStrokingColor(0f, 0f, 0f);
			for (Row row : rows) {
				float x = MARGIN;
				for (int i = 0; i < widths.length; i++) {
					List<String> lines = row.cells().get(i);
					for (int line = 0; line < lines.size(); line++) {
						text(out, fonts.regular(), SIZE, x + PAD, y - PAD - SIZE - line * LINE, lines.get(line));
					}
					x += widths[i];
				}
				y -= row.height();
				rule(out, MARGIN, y, PAGE.getWidth() - MARGIN, y);
			}
		}
	}

	/** Two header rows: the shift names over their three posts, then a label for every column. */
	private float drawHeaders(PDPageContentStream out, float y, float[] widths, PDFont bold) throws IOException {
		float baseline = y - PAD - SIZE;
		float shiftIStart = MARGIN + widths[0] + widths[1] + widths[2];
		float shiftIIStart = shiftIStart + widths[3] + widths[4] + widths[5];
		text(out, bold, SIZE, shiftIStart + PAD, baseline, "Shift I (" + hours(morning) + ")");
		text(out, bold, SIZE, shiftIIStart + PAD, baseline, "Shift II (" + hours(afternoon) + ")");
		y -= HEADER_ROW;
		String[] labels = {"Date", "Day", "Night", "Ward", "Casualty", "General", "Ward", "Casualty", "General",
				"Post-duty off", "Weekly off"};
		float x = MARGIN;
		for (int i = 0; i < widths.length; i++) {
			text(out, bold, SIZE, x + PAD, y - PAD - SIZE, labels[i]);
			x += widths[i];
		}
		y -= HEADER_ROW;
		rule(out, MARGIN, y, PAGE.getWidth() - MARGIN, y);
		return y;
	}

	private static String hours(ShiftType shift) {
		LocalTime end = shift.start().plus(shift.length());
		return shift.start() + "-" + end;
	}

	private static boolean isWeekend(LocalDate day) {
		return day.getDayOfWeek() == DayOfWeek.SATURDAY || day.getDayOfWeek() == DayOfWeek.SUNDAY;
	}

	private static void text(PDPageContentStream out, PDFont font, float size, float x, float y, String words)
			throws IOException {
		out.beginText();
		out.setFont(font, size);
		out.newLineAtOffset(x, y);
		out.showText(words);
		out.endText();
	}

	private static void rule(PDPageContentStream out, float x1, float y1, float x2, float y2) throws IOException {
		out.setStrokingColor(GREY_LINE, GREY_LINE, GREY_LINE);
		out.setLineWidth(0.5f);
		out.moveTo(x1, y1);
		out.lineTo(x2, y2);
		out.stroke();
	}

	/** {@code words} with every character the font cannot draw replaced by {@code ?}. */
	static String printable(String words, PDFont font) {
		StringBuilder kept = new StringBuilder();
		words.codePoints().forEach(codePoint -> {
			String one = new String(Character.toChars(codePoint));
			try {
				font.encode(one);
				kept.append(one);
			} catch (IllegalArgumentException | IOException ex) {
				kept.append('?');
			}
		});
		return kept.toString();
	}

	/** Breaks {@code words} at spaces into lines no wider than {@code width}; a word too long on its own is cut. */
	private static List<String> wrap(String words, float width, PDFont font) throws IOException {
		List<String> lines = new ArrayList<>();
		StringBuilder line = new StringBuilder();
		for (String word : words.split(" ")) {
			String candidate = line.isEmpty() ? word : line + " " + word;
			if (width(candidate, font) <= width) {
				line.setLength(0);
				line.append(candidate);
				continue;
			}
			if (!line.isEmpty()) {
				lines.add(line.toString());
				line.setLength(0);
			}
			String rest = word;
			while (width(rest, font) > width && rest.length() > 1) {
				int cut = rest.length() - 1;
				while (cut > 1 && width(rest.substring(0, cut), font) > width) {
					cut--;
				}
				lines.add(rest.substring(0, cut));
				rest = rest.substring(cut);
			}
			line.append(rest);
		}
		if (!line.isEmpty()) {
			lines.add(line.toString());
		}
		return lines;
	}

	private static float width(String words, PDFont font) throws IOException {
		return font.getStringWidth(words) / 1000 * SIZE;
	}

}
