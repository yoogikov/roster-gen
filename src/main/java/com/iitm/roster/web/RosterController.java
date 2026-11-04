package com.iitm.roster.web;

import com.iitm.roster.constraints.Violation;
import com.iitm.roster.domain.Actor;
import com.iitm.roster.domain.Roster;
import com.iitm.roster.export.Format;
import com.iitm.roster.scheduler.GenerationResult;
import com.iitm.roster.scheduler.MonthInput;
import com.iitm.roster.security.Action;
import com.iitm.roster.security.AuthorizationPolicy;
import com.iitm.roster.security.CurrentActor;
import com.iitm.roster.security.Decision;
import com.iitm.roster.service.EditCommand;
import com.iitm.roster.service.EditResult;
import com.iitm.roster.service.RosterExportService;
import com.iitm.roster.service.RosterGenerationService;
import com.iitm.roster.service.RosterQuery;
import com.iitm.roster.service.RosterService;
import java.time.YearMonth;
import java.util.Locale;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** JSON API for one month: generate, read, edit, publish, export. There is no import. */
@RestController
public class RosterController {

	private final RosterQuery query;
	private final RosterService edits;
	private final RosterGenerationService generation;
	private final RosterExportService exports;
	private final AuthorizationPolicy policy;
	private final CurrentActor current;

	public RosterController(RosterQuery query, RosterService edits, RosterGenerationService generation,
			RosterExportService exports, AuthorizationPolicy policy, CurrentActor current) {
		this.query = query;
		this.edits = edits;
		this.generation = generation;
		this.exports = exports;
		this.policy = policy;
		this.current = current;
	}

	@GetMapping("/api/rosters/{month}")
	ResponseEntity<?> get(@PathVariable String month) {
		YearMonth yearMonth = YearMonth.parse(month);
		Roster roster = query.find(yearMonth).orElse(null);
		if (roster == null) {
			return ResponseEntity.notFound().build();
		}
		ResponseEntity<Map<String, String>> denied = refusal(policy.may(current.get(), new Action.ViewRoster(), roster));
		if (denied != null) {
			return denied;
		}
		return ResponseEntity.ok(roster);
	}

	@PostMapping("/api/rosters/generate")
	ResponseEntity<?> generate(@RequestBody MonthInput input) {
		Actor actor = current.get();
		Roster target = query.find(input.month()).orElseGet(() -> Roster.empty(input.month()));
		ResponseEntity<Map<String, String>> denied = refusal(policy.may(actor, new Action.GenerateRoster(), target));
		if (denied != null) {
			return denied;
		}
		GenerationResult result = generation.generate(input, actor);
		return ResponseEntity.ok(switch (result) {
			case GenerationResult.Ok ok -> new Generated("ok", ok.softPenalty(), 0);
			case GenerationResult.Infeasible infeasible -> new Generated("infeasible", null, infeasible.unfilled().size());
		});
	}

	@PostMapping("/api/rosters/{month}/edits")
	ResponseEntity<?> edit(@PathVariable String month, @RequestBody EditBody body) {
		YearMonth yearMonth = YearMonth.parse(month);
		if (!YearMonth.from(body.date()).equals(yearMonth)) {
			throw new IllegalArgumentException("edit date is not in " + yearMonth);
		}
		Roster roster = query.find(yearMonth)
				.orElseThrow(() -> new IllegalArgumentException("no roster for " + yearMonth));
		Actor actor = current.get();
		EditCommand command = body.toCommand();
		ResponseEntity<Map<String, String>> denied = refusal(
				policy.may(actor, new Action.EditShift(command.slot(), body.staffIds()), roster));
		if (denied != null) {
			return denied;
		}
		EditResult result = edits.applyEdit(command, actor);
		return switch (result) {
			case EditResult.Applied applied -> ResponseEntity.ok(new Messages("applied",
					applied.warnings().stream().map(Violation::message).toList()));
			case EditResult.Rejected rejected -> ResponseEntity.status(HttpStatus.UNPROCESSABLE_CONTENT).body(new Messages(
					"rejected", rejected.violations().stream().map(v -> v.constraintId() + ": " + v.message()).toList()));
		};
	}

	@PostMapping("/api/rosters/{month}/publish")
	ResponseEntity<?> publish(@PathVariable String month) {
		YearMonth yearMonth = YearMonth.parse(month);
		Roster roster = query.find(yearMonth)
				.orElseThrow(() -> new IllegalArgumentException("no roster for " + yearMonth));
		Actor actor = current.get();
		ResponseEntity<Map<String, String>> denied = refusal(policy.may(actor, new Action.PublishRoster(), roster));
		if (denied != null) {
			return denied;
		}
		return ResponseEntity.ok(edits.publish(yearMonth, actor));
	}

	@GetMapping("/api/rosters/{month}/export")
	ResponseEntity<?> export(@PathVariable String month, @RequestParam(defaultValue = "csv") String format) {
		YearMonth yearMonth = YearMonth.parse(month);
		Roster roster = query.find(yearMonth)
				.orElseThrow(() -> new IllegalArgumentException("no roster for " + yearMonth));
		ResponseEntity<Map<String, String>> denied = refusal(policy.may(current.get(), new Action.ViewRoster(), roster));
		if (denied != null) {
			return denied;
		}
		Format parsed = Format.valueOf(format.toUpperCase(Locale.ROOT));
		byte[] body = exports.export(yearMonth, parsed);
		return ResponseEntity.ok()
				.header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=roster-" + yearMonth + ".csv")
				.contentType(MediaType.parseMediaType("text/csv"))
				.body(body);
	}

	/** Null when the actor may proceed. */
	private static ResponseEntity<Map<String, String>> refusal(Decision decision) {
		if (decision instanceof Decision.Allow) {
			return null;
		}
		return Decisions.forbidden(decision);
	}

	/** What generate did. {@code softPenalty} is set only when every slot was filled. */
	public record Generated(String outcome, Integer softPenalty, int unfilled) {
	}

	/** Warnings on an applied edit, or the hard violations that rejected one. */
	public record Messages(String outcome, List<String> messages) {

		public Messages {
			messages = List.copyOf(messages);
		}

	}

}
