package com.iitm.roster.repo;

import com.iitm.roster.domain.Roster;
import com.iitm.roster.domain.RosterStatus;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Table;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;

/**
 * One month's roster. The month is stored as text ({@code 2026-07}) because Hibernate has
 * no built-in mapping for {@link YearMonth}. {@code list_index} keeps the assignments in
 * the order the domain roster had them, so a reloaded roster is equal to the saved one.
 */
@Entity
@Table(name = "roster")
class RosterEntity {

	@Id
	@GeneratedValue
	private Long id;

	@Column(name = "roster_month", nullable = false, unique = true, length = 7)
	private String month;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private RosterStatus status;

	@ElementCollection
	@CollectionTable(name = "roster_assignment", joinColumns = @JoinColumn(name = "roster_id"))
	@OrderColumn(name = "list_index")
	private List<AssignmentEmbeddable> assignments = new ArrayList<>();

	protected RosterEntity() {
		// Required by JPA, which instantiates the class before setting fields; not for application code.
	}

	RosterEntity(YearMonth month) {
		this.month = month.toString();
	}

	void update(RosterStatus newStatus, List<AssignmentEmbeddable> newAssignments) {
		this.status = newStatus;
		this.assignments.clear();
		this.assignments.addAll(newAssignments);
	}

	Roster toDomain() {
		return new Roster(YearMonth.parse(month), status,
				assignments.stream().map(AssignmentEmbeddable::toDomain).toList());
	}

}
