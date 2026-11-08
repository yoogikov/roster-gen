package com.iitm.roster.web;

import com.iitm.roster.constraints.Violation;
import com.iitm.roster.domain.Actor;
import com.iitm.roster.domain.Assignment;
import com.iitm.roster.domain.HospitalCalendar;
import com.iitm.roster.domain.Leave;
import com.iitm.roster.domain.Post;
import com.iitm.roster.domain.Role;
import com.iitm.roster.domain.Roster;
import com.iitm.roster.domain.RosterStatus;
import com.iitm.roster.domain.Staff;
import com.iitm.roster.domain.StaffRole;
import com.iitm.roster.requests.ChangeLifecycle;
import com.iitm.roster.requests.ChangeResult;
import com.iitm.roster.requests.LeaveLifecycle;
import com.iitm.roster.requests.LeaveResult;
import com.iitm.roster.requests.RequestInbox;
import com.iitm.roster.requests.SwapLifecycle;
import com.iitm.roster.requests.SwapResult;
import com.iitm.roster.rules.InvalidRuleException;
import com.iitm.roster.rules.RuleSpec;
import com.iitm.roster.scheduler.GenerationResult;
import com.iitm.roster.scheduler.MonthInput;
import com.iitm.roster.security.Action;
import com.iitm.roster.security.AuthorizationPolicy;
import com.iitm.roster.security.CurrentActor;
import com.iitm.roster.security.Decision;
import com.iitm.roster.service.EditCommand;
import com.iitm.roster.service.EditResult;
import com.iitm.roster.repo.StaffStore;
import com.iitm.roster.service.RosterGenerationService;
import com.iitm.roster.service.RosterQuery;
import com.iitm.roster.service.RosterService;
import com.iitm.roster.service.RuleBook;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

/** Thymeleaf pages: the roster sheet, audit log, request inbox, rule editor and leave form. */
@Controller
@SuppressFBWarnings(value = "EI_EXPOSE_REP2",
		justification = "the services and stores are Spring singletons injected on purpose; sharing them is the point")
public class PageController {

	private final RosterQuery query;
	private final RosterService edits;
	private final RosterGenerationService generation;
	private final AuthorizationPolicy policy;
	private final CurrentActor current;
	private final ChangeLifecycle changes;
	private final LeaveLifecycle leaves;
	private final SwapLifecycle swaps;
	private final RequestInbox inbox;
	private final RuleBook rules;
	private final StaffStore staff;

	public PageController(RosterQuery query, RosterService edits, RosterGenerationService generation,
			AuthorizationPolicy policy, CurrentActor current, ChangeLifecycle changes, LeaveLifecycle leaves,
			SwapLifecycle swaps, RequestInbox inbox, RuleBook rules, StaffStore staff) {
		this.query = query;
		this.edits = edits;
		this.generation = generation;
		this.policy = policy;
		this.current = current;
		this.changes = changes;
		this.leaves = leaves;
		this.swaps = swaps;
		this.inbox = inbox;
		this.rules = rules;
		this.staff = staff;
	}

	@GetMapping("/")
	String home(Model model) {
		model.addAttribute("admin", admin());
		model.addAttribute("doctorLines", doctorLines());
		return "home";
	}

	@PostMapping("/rosters/generate")
	String generate(@RequestParam String month, @RequestParam String doctors,
			@RequestParam(defaultValue = "") String firstOnNights,
			@RequestParam int weekday, @RequestParam int weekend, @RequestParam(defaultValue = "") String leave,
			@RequestParam(defaultValue = "") String holidays, Model model) {
		model.addAttribute("admin", admin());
		model.addAttribute("doctorLines", doctorLines());
		if (!admin()) {
			model.addAttribute("notice", "only an admin can generate a roster");
			return "home";
		}
		try {
			List<Staff> rostered = parseDoctors(doctors);
			YearMonth yearMonth = YearMonth.parse(month);
			String continued = "";
			Staff first;
			if (firstOnNights.isBlank()) {
				first = generation.continueNights(yearMonth, rostered).orElseThrow(() -> new IllegalArgumentException(
						"no roster for " + yearMonth.minusMonths(1)
								+ " to continue the nights from; type who does the first night"));
				continued = "; nights start with " + first.id() + ", continuing from " + yearMonth.minusMonths(1);
			} else {
				first = rostered.stream().filter(doctor -> doctor.id().equals(firstOnNights.trim())).findFirst()
						.orElseThrow(() -> new IllegalArgumentException("first on nights is not in the doctor list"));
			}
			MonthInput input = new MonthInput(yearMonth, rostered, first, parseLeave(leave, rostered),
					calendar(holidays, yearMonth), weekday, weekend);
			GenerationResult result = generation.generate(input, current.get());
			model.addAttribute("notice", switch (result) {
				case GenerationResult.Ok ok -> "generated " + month + ", soft penalty " + ok.softPenalty();
				case GenerationResult.Infeasible infeasible -> month + " left " + infeasible.unfilled().size() + " slots unfilled";
			} + continued + brokenText(generation.brokenRules(yearMonth)) + owedText(yearMonth));
		} catch (RuntimeException ex) {
			model.addAttribute("notice", ex.getMessage());
		}
		return "home";
	}

	@GetMapping("/rosters/{month}")
	String roster(@PathVariable String month, Model model) {
		YearMonth yearMonth = YearMonth.parse(month);
		Roster roster = query.find(yearMonth).orElse(null);
		if (roster == null) {
			model.addAttribute("missing", true);
			model.addAttribute("month", month);
			return "roster";
		}
		Actor actor = current.get();
		boolean canEdit = actor.role() == Role.ADMIN;
		boolean staffForms = actor.role() == Role.STAFF && roster.status() == RosterStatus.PUBLISHED
				&& actor.staffId().isPresent();
		model.addAttribute("month", month);
		model.addAttribute("status", roster.status().name());
		model.addAttribute("rows", RosterGrid.rows(roster));
		model.addAttribute("owedFromMonth", yearMonth.minusMonths(1).toString());
		model.addAttribute("owedFrom", owedNames(generation.owedOffsInto(yearMonth)));
		model.addAttribute("owedToMonth", yearMonth.plusMonths(1).toString());
		model.addAttribute("owedTo", owedNames(generation.owedOffsAfter(yearMonth)));
		model.addAttribute("canEdit", canEdit);
		model.addAttribute("staffForms", staffForms);
		model.addAttribute("doctors", staff.findActive());
		if (staffForms) {
			String staffId = actor.staffId().orElseThrow();
			model.addAttribute("myShifts", SheetShifts.mine(roster, staffId));
			model.addAttribute("otherShifts", SheetShifts.others(roster, staffId));
		}
		model.addAttribute("missing", false);
		return "roster";
	}

	@PostMapping("/rosters/{month}/edits")
	String edit(@PathVariable String month, @RequestParam String type, @RequestParam LocalDate date,
			@RequestParam String shift, @RequestParam(required = false) String staffId,
			@RequestParam(required = false) String fromStaffId, @RequestParam(required = false) String toStaffId,
			@RequestParam(defaultValue = "GENERAL") Post post, Model model) {
		String notice;
		try {
			EditBody body = new EditBody(type, date, shift, staffId, fromStaffId, toStaffId, post);
			EditCommand command = body.toCommand();
			YearMonth yearMonth = YearMonth.parse(month);
			Roster roster = query.find(yearMonth).orElseThrow(() -> new IllegalArgumentException("no roster for " + month));
			Actor actor = current.get();
			Decision decision = policy.may(actor, new Action.EditShift(body.slot(), body.staffIds()), roster);
			notice = switch (decision) {
				case Decision.Allow() -> applied(edits.applyEdit(command, actor));
				case Decision.RequiresApproval needs -> needs.approver() + ": " + filed(changes.file(command, actor));
				case Decision.Deny deny -> deny.reason();
			};
		} catch (RuntimeException ex) {
			notice = ex.getMessage();
		}
		roster(month, model);
		model.addAttribute("notice", notice);
		return "roster";
	}

	@PostMapping("/rosters/{month}/weekly-offs")
	String moveWeeklyOff(@PathVariable String month, @RequestParam String staffId, @RequestParam LocalDate fromDate,
			@RequestParam LocalDate toDate, Model model) {
		String notice;
		try {
			YearMonth yearMonth = YearMonth.parse(month);
			Roster roster = query.find(yearMonth).orElseThrow(() -> new IllegalArgumentException("no roster for " + month));
			Actor actor = current.get();
			EditCommand command = new EditCommand.MoveWeeklyOff(fromDate, staffId, toDate);
			Decision decision = policy.may(actor, new Action.EditWeeklyOff(fromDate, toDate, staffId), roster);
			notice = switch (decision) {
				case Decision.Allow() -> applied(edits.applyEdit(command, actor));
				case Decision.RequiresApproval needs -> "needs " + needs.approver() + " approval";
				case Decision.Deny deny -> deny.reason();
			};
		} catch (RuntimeException ex) {
			notice = ex.getMessage();
		}
		roster(month, model);
		model.addAttribute("notice", notice);
		return "roster";
	}

	@PostMapping("/rosters/{month}/swaps")
	String proposeSwap(@PathVariable String month, @RequestParam String offered, @RequestParam String wanted,
			Model model) {
		String notice;
		try {
			YearMonth yearMonth = YearMonth.parse(month);
			Roster roster = query.find(yearMonth).orElseThrow(() -> new IllegalArgumentException("no roster for " + month));
			Assignment offer = SheetShifts.parse(roster, offered);
			Assignment want = SheetShifts.parse(roster, wanted);
			notice = text(swaps.propose(offer, want, current.get()));
		} catch (RuntimeException ex) {
			notice = ex.getMessage();
		}
		roster(month, model);
		model.addAttribute("notice", notice);
		return "roster";
	}

	@PostMapping("/rosters/{month}/publish")
	String publish(@PathVariable String month, Model model) {
		String notice;
		try {
			YearMonth yearMonth = YearMonth.parse(month);
			Roster roster = query.find(yearMonth).orElseThrow(() -> new IllegalArgumentException("no roster for " + month));
			Actor actor = current.get();
			Decision decision = policy.may(actor, new Action.PublishRoster(), roster);
			notice = switch (decision) {
				case Decision.Allow() -> {
					edits.publish(yearMonth, actor);
					yield month + " is published";
				}
				case Decision.Deny deny -> deny.reason();
				case Decision.RequiresApproval needs -> "needs " + needs.approver() + " approval";
			};
		} catch (RuntimeException ex) {
			notice = ex.getMessage();
		}
		roster(month, model);
		model.addAttribute("notice", notice);
		return "roster";
	}

	@GetMapping("/audit")
	String audit(Model model) {
		model.addAttribute("events", query.audit());
		return "audit";
	}

	@GetMapping("/inbox")
	String inbox(Model model) {
		Actor actor = current.get();
		model.addAttribute("open", inbox.open());
		model.addAttribute("mine", inbox.mine(actor));
		model.addAttribute("admin", admin());
		model.addAttribute("actor", actor);
		model.addAttribute("staff", actor.role() == Role.STAFF);
		return "inbox";
	}

	@PostMapping("/inbox/swaps/{id}/accept")
	String acceptSwap(@PathVariable String id, Model model) {
		return inboxNotice(model, text(swaps.accept(id, current.get())));
	}

	@PostMapping("/inbox/swaps/{id}/approve")
	String approveSwap(@PathVariable String id, Model model) {
		return inboxNotice(model, text(swaps.approve(id, current.get())));
	}

	@PostMapping("/inbox/swaps/{id}/reject")
	String rejectSwap(@PathVariable String id, Model model) {
		return inboxNotice(model, text(swaps.reject(id, current.get())));
	}

	@PostMapping("/inbox/changes/{id}/approve")
	String approveChange(@PathVariable String id, Model model) {
		return inboxNotice(model, text(changes.approve(id, current.get())));
	}

	@PostMapping("/inbox/changes/{id}/reject")
	String rejectChange(@PathVariable String id, Model model) {
		return inboxNotice(model, text(changes.reject(id, current.get())));
	}

	@PostMapping("/inbox/leaves/{id}/approve")
	String approveLeave(@PathVariable String id, Model model) {
		return inboxNotice(model, text(leaves.approve(id, current.get())));
	}

	@PostMapping("/inbox/leaves/{id}/reject")
	String rejectLeave(@PathVariable String id, Model model) {
		return inboxNotice(model, text(leaves.reject(id, current.get())));
	}

	@GetMapping("/rules")
	String rules(@RequestParam(defaultValue = "") String month, Model model) {
		model.addAttribute("templates", rules.templates());
		model.addAttribute("month", month);
		if (!month.isBlank()) {
			model.addAttribute("saved", rules.saved(YearMonth.parse(month)));
		}
		return "rules";
	}

	@PostMapping("/rules")
	String saveRule(@RequestParam String month, @RequestParam String templateId,
			@RequestParam(required = false) String weekday, @RequestParam(required = false) String weekend, Model model) {
		Map<String, String> params = "weekday-staffing".equals(templateId)
				? Map.of("weekday", weekday == null ? "" : weekday, "weekend", weekend == null ? "" : weekend)
				: Map.of();
		String notice;
		try {
			rules.save(YearMonth.parse(month), new RuleSpec(templateId, params));
			notice = "saved " + templateId + " for " + month;
		} catch (InvalidRuleException | RuntimeException ex) {
			notice = ex.getMessage();
		}
		rules(month, model);
		model.addAttribute("notice", notice);
		return "rules";
	}

	@GetMapping("/leave")
	String leaveForm(Model model) {
		model.addAttribute("staffId", current.get().staffId().orElse(""));
		return "leave";
	}

	@PostMapping("/leave")
	String requestLeave(@RequestParam String staffId, @RequestParam LocalDate firstDay, @RequestParam LocalDate lastDay,
			Model model) {
		model.addAttribute("staffId", staffId);
		try {
			model.addAttribute("notice", text(leaves.propose(staffId, firstDay, lastDay, current.get())));
		} catch (RuntimeException ex) {
			model.addAttribute("notice", ex.getMessage());
		}
		return "leave";
	}

	private String inboxNotice(Model model, String notice) {
		inbox(model);
		model.addAttribute("notice", notice);
		return "inbox";
	}

	private boolean admin() {
		return current.get().role() == Role.ADMIN;
	}

	/** Active doctors as {@code id,name} lines for the generate form. */
	private String doctorLines() {
		List<Staff> active = staff.findActive();
		if (active.isEmpty()) {
			return "";
		}
		return active.stream().map(doctor -> doctor.id() + "," + doctor.name()).collect(Collectors.joining("\n"));
	}

	private static List<Staff> parseDoctors(String text) {
		List<Staff> doctors = new ArrayList<>();
		for (String line : text.split("\\R")) {
			String trimmed = line.trim();
			if (trimmed.isEmpty()) {
				continue;
			}
			String[] parts = trimmed.split(",", 2);
			if (parts.length < 2 || parts[1].isBlank()) {
				throw new IllegalArgumentException("each doctor is an id and a name, separated by a comma");
			}
			doctors.add(new Staff(parts[0].trim(), parts[1].trim(), StaffRole.DOCTOR));
		}
		if (doctors.isEmpty()) {
			throw new IllegalArgumentException("name at least one doctor");
		}
		return doctors;
	}

	/**
	 * The holidays typed on the Generate page, separated by commas or new lines. A blank box keeps
	 * the holidays the month was last generated with, so generating again does not lose them;
	 * {@code none} clears them.
	 */
	private HospitalCalendar calendar(String text, YearMonth month) {
		String trimmed = text.trim();
		if (trimmed.isEmpty()) {
			return generation.calendarFor(month);
		}
		if ("none".equalsIgnoreCase(trimmed)) {
			return HospitalCalendar.noHolidays();
		}
		Set<LocalDate> days = new HashSet<>();
		for (String part : trimmed.split("[,\\s]+")) {
			LocalDate day;
			try {
				day = LocalDate.parse(part);
			} catch (DateTimeParseException ex) {
				throw new IllegalArgumentException("holidays are yyyy-MM-dd: " + trimmed, ex);
			}
			if (!YearMonth.from(day).equals(month)) {
				throw new IllegalArgumentException("holiday " + day + " is not in " + month);
			}
			days.add(day);
		}
		return new HospitalCalendar(days);
	}

	/** One leave per line: doctor id, first day, last day (yyyy-MM-dd). Blank lines are skipped. */
	private static List<Leave> parseLeave(String text, List<Staff> doctors) {
		Map<String, Staff> byId = doctors.stream().collect(Collectors.toMap(Staff::id, doctor -> doctor, (a, b) -> a));
		List<Leave> leaves = new ArrayList<>();
		for (String line : text.split("\\R")) {
			String trimmed = line.trim();
			if (trimmed.isEmpty()) {
				continue;
			}
			String[] parts = trimmed.split(",");
			if (parts.length != 3) {
				throw new IllegalArgumentException("each leave is a doctor id, a first day and a last day: " + trimmed);
			}
			Staff doctor = byId.get(parts[0].trim());
			if (doctor == null) {
				throw new IllegalArgumentException("leave for " + parts[0].trim() + ", who is not in the doctor list");
			}
			try {
				leaves.add(new Leave(doctor, LocalDate.parse(parts[1].trim()), LocalDate.parse(parts[2].trim())));
			} catch (DateTimeParseException ex) {
				throw new IllegalArgumentException("leave days are yyyy-MM-dd: " + trimmed, ex);
			}
		}
		return leaves;
	}

	private static String applied(EditResult result) {
		return switch (result) {
			case EditResult.Applied applied -> applied.warnings().isEmpty()
					? "applied"
					: applied.warnings().get(0).message();
			case EditResult.Rejected rejected -> rejected.violations().get(0).constraintId() + ": "
					+ rejected.violations().get(0).message();
		};
	}

	private static String filed(ChangeResult result) {
		return switch (result) {
			case ChangeResult.Pending pending -> "stored for approval: " + changeText(pending.request());
			case ChangeResult.NotAllowed notAllowed -> decisionText(notAllowed.decision());
			case ChangeResult.Approved approved -> "approved: " + changeText(approved.request());
			case ChangeResult.Rejected rejected -> "rejected: " + changeText(rejected.request());
			case ChangeResult.EditRejected editRejected -> editRejected.edit().violations().get(0).message();
		};
	}

	private static String text(SwapResult result) {
		return switch (result) {
			case SwapResult.Pending pending -> "swap proposed: " + swapText(pending.request());
			case SwapResult.Accepted accepted -> "swap accepted: " + swapText(accepted.request());
			case SwapResult.Approved approved -> "swap approved: " + swapText(approved.request());
			case SwapResult.Rejected rejected -> "swap rejected: " + swapText(rejected.request());
			case SwapResult.NotAllowed notAllowed -> decisionText(notAllowed.decision());
			case SwapResult.Refused refused -> refused.reason();
			case SwapResult.EditRejected editRejected -> editRejected.edit().violations().get(0).message();
		};
	}

	private static String text(ChangeResult result) {
		return filed(result);
	}

	private static String text(LeaveResult result) {
		return switch (result) {
			case LeaveResult.Pending pending -> "leave requested for " + pending.request().staff().name() + " from "
					+ pending.request().firstDay() + " to " + pending.request().lastDay();
			case LeaveResult.Approved approved -> approvedText(approved);
			case LeaveResult.Rejected rejected -> "leave rejected for " + rejected.request().staff().name() + " from "
					+ rejected.request().firstDay() + " to " + rejected.request().lastDay();
			case LeaveResult.NotAllowed notAllowed -> decisionText(notAllowed.decision());
			case LeaveResult.Refused refused -> refused.reason();
		};
	}

	private static String swapText(com.iitm.roster.domain.SwapRequest request) {
		return assignmentText(request.offered()) + " for " + assignmentText(request.wanted());
	}

	private static String assignmentText(Assignment assignment) {
		return assignment.staff().name() + " " + assignment.slot().shift().code() + " on " + assignment.slot().date();
	}

	private static String changeText(com.iitm.roster.domain.ChangeRequest request) {
		String who = request.staffId() != null ? request.staffId()
				: request.fromStaffId() + " → " + request.toStaffId();
		return request.type() + " " + request.shiftCode() + " on " + request.date() + " (" + who + ")";
	}

	private static String brokenText(List<Violation> broken) {
		if (broken.isEmpty()) {
			return "";
		}
		return "; " + broken.size() + " HARD rule" + (broken.size() == 1 ? "" : "s") + " broken: "
				+ broken.stream().map(Violation::message).collect(Collectors.joining("; "));
	}

	private String owedText(YearMonth month) {
		List<Staff> owed = generation.owedOffsAfter(month);
		if (owed.isEmpty()) {
			return "";
		}
		return "; weekly offs owed to " + month.plusMonths(1) + ": " + owedNames(owed);
	}

	/** Doctors' names in the order given, with a count for anyone owed more than one; blank if none. */
	private static String owedNames(List<Staff> owed) {
		Map<String, Long> counts = owed.stream()
				.collect(Collectors.groupingBy(Staff::name, LinkedHashMap::new, Collectors.counting()));
		return counts.entrySet().stream()
				.map(entry -> entry.getValue() == 1 ? entry.getKey() : entry.getKey() + " x" + entry.getValue())
				.collect(Collectors.joining(", "));
	}

	private static String approvedText(LeaveResult.Approved approved) {
		String text = "leave approved for " + approved.request().staff().name() + " from "
				+ approved.request().firstDay() + " to " + approved.request().lastDay();
		if (approved.regenerated()) {
			return text + "; the draft roster was generated again with it";
		}
		if (approved.freed().isEmpty()) {
			return text;
		}
		return text + "; " + approved.request().staff().name() + " was taken off " + approved.freed().size()
				+ " shifts, now to fill: " + approved.freed().stream()
						.map(assignment -> assignment.slot().date() + " " + assignment.slot().shift().code())
						.collect(Collectors.joining(", "));
	}

	private static String decisionText(Decision decision) {
		return switch (decision) {
			case Decision.Deny deny -> deny.reason();
			case Decision.RequiresApproval needs -> "needs " + needs.approver() + " approval";
			case Decision.Allow() -> "allowed";
		};
	}

}
