package com.iitm.roster.service;

import com.iitm.roster.domain.AuditEvent;
import com.iitm.roster.domain.Roster;
import com.iitm.roster.repo.AuditStore;
import com.iitm.roster.repo.RosterStore;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.time.YearMonth;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Reads rosters and the audit log. The web layer is not allowed to call {@code repo} itself. */
@Service
@Transactional(readOnly = true)
@SuppressFBWarnings(value = "EI_EXPOSE_REP2",
		justification = "the stores are Spring singletons injected on purpose; sharing them is the point")
public class RosterQuery {

	private final RosterStore rosters;
	private final AuditStore audit;

	public RosterQuery(RosterStore rosters, AuditStore audit) {
		this.rosters = rosters;
		this.audit = audit;
	}

	public Optional<Roster> find(YearMonth month) {
		return rosters.findByMonth(month);
	}

	/** Every audit event, oldest first. */
	public List<AuditEvent> audit() {
		return audit.findAll();
	}

}
