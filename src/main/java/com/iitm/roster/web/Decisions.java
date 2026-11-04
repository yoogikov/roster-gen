package com.iitm.roster.web;

import com.iitm.roster.security.Decision;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/** Turns a policy refusal into HTTP 403. Allow is not a refusal. */
final class Decisions {

	private Decisions() {
	}

	static ResponseEntity<Map<String, String>> forbidden(Decision decision) {
		Map<String, String> body = new LinkedHashMap<>();
		switch (decision) {
			case Decision.Deny deny -> {
				body.put("decision", "Deny");
				body.put("reason", deny.reason());
			}
			case Decision.RequiresApproval needs -> {
				body.put("decision", "RequiresApproval");
				body.put("approver", needs.approver().name());
			}
			case Decision.Allow() -> throw new IllegalArgumentException("Allow is not a refusal");
		}
		return ResponseEntity.status(HttpStatus.FORBIDDEN).body(body);
	}

}
