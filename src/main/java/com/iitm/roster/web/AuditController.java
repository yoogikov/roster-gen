package com.iitm.roster.web;

import com.iitm.roster.domain.AuditEvent;
import com.iitm.roster.service.RosterQuery;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** The audit log. Any signed-in user may read it; there is no roster to ask the policy about. */
@RestController
public class AuditController {

	private final RosterQuery query;

	public AuditController(RosterQuery query) {
		this.query = query;
	}

	@GetMapping("/api/audit")
	List<AuditEvent> audit() {
		return query.audit();
	}

}
