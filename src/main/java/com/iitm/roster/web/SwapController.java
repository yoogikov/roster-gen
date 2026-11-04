package com.iitm.roster.web;

import com.iitm.roster.domain.Assignment;
import com.iitm.roster.domain.Post;
import com.iitm.roster.domain.Roster;
import com.iitm.roster.domain.SwapRequest;
import com.iitm.roster.domain.ShiftSlot;
import com.iitm.roster.domain.ShiftType;
import com.iitm.roster.domain.StandardShifts;
import com.iitm.roster.requests.SwapLifecycle;
import com.iitm.roster.requests.SwapResult;
import com.iitm.roster.security.CurrentActor;
import com.iitm.roster.service.RosterQuery;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/** Propose, accept, approve or reject a swap. Reading one shows whether the lapse job has closed it. */
@RestController
public class SwapController {

	private final SwapLifecycle swaps;
	private final RosterQuery query;
	private final CurrentActor current;

	public SwapController(SwapLifecycle swaps, RosterQuery query, CurrentActor current) {
		this.swaps = swaps;
		this.query = query;
		this.current = current;
	}

	@PostMapping("/api/swaps")
	ResponseEntity<?> propose(@RequestBody ProposeBody body) {
		YearMonth month = YearMonth.from(body.offeredDate());
		if (!YearMonth.from(body.wantedDate()).equals(month)) {
			throw new IllegalArgumentException("a swap must stay inside one month");
		}
		Roster roster = query.find(month).orElseThrow(() -> new IllegalArgumentException("no roster for " + month));
		Assignment offered = find(roster, body.offeredDate(), body.offeredShift(), body.offeredStaffId(), body.offeredPost());
		Assignment wanted = find(roster, body.wantedDate(), body.wantedShift(), body.wantedStaffId(), body.wantedPost());
		return reply(swaps.propose(offered, wanted, current.get()));
	}

	@GetMapping("/api/swaps/{id}")
	Map<String, String> get(@PathVariable String id) {
		SwapRequest request = swaps.get(id);
		return view(request.id(), request.state().name(), null);
	}

	@PostMapping("/api/swaps/{id}/accept")
	ResponseEntity<?> accept(@PathVariable String id) {
		return reply(swaps.accept(id, current.get()));
	}

	@PostMapping("/api/swaps/{id}/approve")
	ResponseEntity<?> approve(@PathVariable String id) {
		return reply(swaps.approve(id, current.get()));
	}

	@PostMapping("/api/swaps/{id}/reject")
	ResponseEntity<?> reject(@PathVariable String id) {
		return reply(swaps.reject(id, current.get()));
	}

	private static ResponseEntity<?> reply(SwapResult result) {
		return switch (result) {
			case SwapResult.Pending pending -> ResponseEntity.ok(view(pending.request().id(), pending.request().state().name(), null));
			case SwapResult.Accepted accepted -> ResponseEntity.ok(view(accepted.request().id(), accepted.request().state().name(), null));
			case SwapResult.Approved approved -> ResponseEntity.ok(view(approved.request().id(), approved.request().state().name(), null));
			case SwapResult.Rejected rejected -> ResponseEntity.ok(view(rejected.request().id(), rejected.request().state().name(), null));
			case SwapResult.NotAllowed notAllowed -> Decisions.forbidden(notAllowed.decision());
			case SwapResult.Refused refused -> ResponseEntity.unprocessableContent().body(view(null, null, refused.reason()));
			case SwapResult.EditRejected editRejected -> ResponseEntity.unprocessableContent().body(view(
					editRejected.request().id(), editRejected.request().state().name(),
					editRejected.edit().violations().get(0).message()));
		};
	}

	private static Map<String, String> view(String id, String state, String reason) {
		Map<String, String> body = new LinkedHashMap<>();
		if (id != null) {
			body.put("id", id);
		}
		if (state != null) {
			body.put("state", state);
		}
		if (reason != null) {
			body.put("reason", reason);
		}
		return body;
	}

	private static Assignment find(Roster roster, LocalDate date, String shift, String staffId, Post post) {
		ShiftType type = switch (shift) {
			case "I" -> StandardShifts.SHIFT_I;
			case "II" -> StandardShifts.SHIFT_II;
			case "N" -> StandardShifts.NIGHT;
			default -> throw new IllegalArgumentException("unknown shift: " + shift);
		};
		ShiftSlot slot = new ShiftSlot(date, type);
		Post wanted = post == null ? Post.GENERAL : post;
		return roster.assignmentsOn(slot).stream()
				.filter(assignment -> assignment.staff().id().equals(staffId) && assignment.post() == wanted)
				.findFirst()
				.orElseThrow(() -> new IllegalArgumentException(staffId + " is not on " + shift + " (" + wanted + ") on " + date));
	}

}
