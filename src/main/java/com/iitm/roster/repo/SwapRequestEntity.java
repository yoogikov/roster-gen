package com.iitm.roster.repo;

import com.iitm.roster.domain.RequestState;
import com.iitm.roster.domain.SwapRequest;
import jakarta.persistence.AssociationOverride;
import jakarta.persistence.AttributeOverride;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import java.time.Instant;

/** A swap request; its two assignments get {@code offered_} and {@code wanted_} column prefixes. */
@Entity
@Table(name = "swap_request")
class SwapRequestEntity {

	@Id
	private String id;

	@Embedded
	@AttributeOverride(name = "date", column = @Column(name = "offered_date", nullable = false))
	@AttributeOverride(name = "shiftCode", column = @Column(name = "offered_shift_code", nullable = false))
	@AttributeOverride(name = "shiftStart", column = @Column(name = "offered_shift_start", nullable = false))
	@AttributeOverride(name = "shiftLength", column = @Column(name = "offered_shift_length", nullable = false))
	@AssociationOverride(name = "staff", joinColumns = @JoinColumn(name = "offered_staff_id", nullable = false))
	private AssignmentEmbeddable offered;

	@Embedded
	@AttributeOverride(name = "date", column = @Column(name = "wanted_date", nullable = false))
	@AttributeOverride(name = "shiftCode", column = @Column(name = "wanted_shift_code", nullable = false))
	@AttributeOverride(name = "shiftStart", column = @Column(name = "wanted_shift_start", nullable = false))
	@AttributeOverride(name = "shiftLength", column = @Column(name = "wanted_shift_length", nullable = false))
	@AssociationOverride(name = "staff", joinColumns = @JoinColumn(name = "wanted_staff_id", nullable = false))
	private AssignmentEmbeddable wanted;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private RequestState state;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	protected SwapRequestEntity() {
		// Required by JPA, which instantiates the class before setting fields; not for application code.
	}

	SwapRequestEntity(String id) {
		this.id = id;
	}

	void update(SwapRequest request, AssignmentEmbeddable newOffered, AssignmentEmbeddable newWanted) {
		this.offered = newOffered;
		this.wanted = newWanted;
		this.state = request.state();
		this.createdAt = request.createdAt();
	}

	SwapRequest toDomain() {
		return new SwapRequest(id, offered.toDomain(), wanted.toDomain(), state, createdAt);
	}

}
