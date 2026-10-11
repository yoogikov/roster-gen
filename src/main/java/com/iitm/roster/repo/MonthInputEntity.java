package com.iitm.roster.repo;

import com.iitm.roster.domain.HospitalCalendar;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** A month's generation input. {@code list_index} keeps the doctors in night-rotation order. */
@Entity
@Table(name = "month_input")
class MonthInputEntity {

	@Id
	@GeneratedValue
	private Long id;

	@Column(name = "roster_month", nullable = false, unique = true, length = 7)
	private String month;

	@ManyToMany
	@JoinTable(name = "month_input_doctor",
			joinColumns = @JoinColumn(name = "month_input_id"),
			inverseJoinColumns = @JoinColumn(name = "staff_id"))
	@OrderColumn(name = "list_index")
	private List<StaffEntity> doctors = new ArrayList<>();

	@ManyToOne(optional = false)
	@JoinColumn(name = "first_on_nights_id", nullable = false)
	private StaffEntity firstOnNights;

	@ElementCollection
	@CollectionTable(name = "month_input_leave", joinColumns = @JoinColumn(name = "month_input_id"))
	@OrderColumn(name = "list_index")
	private List<LeaveEmbeddable> leaves = new ArrayList<>();

	@ElementCollection
	@CollectionTable(name = "month_input_holiday", joinColumns = @JoinColumn(name = "month_input_id"))
	@Column(name = "holiday", nullable = false)
	private Set<LocalDate> holidays = new HashSet<>();

	@Column(name = "weekday_per_shift", nullable = false)
	private int weekdayPerShift;

	@Column(name = "weekend_per_shift", nullable = false)
	private int weekendPerShift;

	protected MonthInputEntity() {
		// Required by JPA, which instantiates the class before setting fields; not for application code.
	}

	MonthInputEntity(YearMonth month) {
		this.month = month.toString();
	}

	void update(StoredMonthInput input, List<StaffEntity> newDoctors, StaffEntity newFirstOnNights,
			List<LeaveEmbeddable> newLeaves) {
		doctors.clear();
		doctors.addAll(newDoctors);
		firstOnNights = newFirstOnNights;
		leaves.clear();
		leaves.addAll(newLeaves);
		// A new set, not clear() + addAll(): clearing this set in place left the old rows in
		// the table (MonthInputStoreTest.savingTheSameMonthAgainReplacesTheInput).
		holidays = new HashSet<>(input.calendar().holidays());
		weekdayPerShift = input.weekdayPerShift();
		weekendPerShift = input.weekendPerShift();
	}

	StoredMonthInput toDomain() {
		return new StoredMonthInput(YearMonth.parse(month),
				doctors.stream().map(StaffEntity::toDomain).toList(),
				firstOnNights.toDomain(),
				leaves.stream().map(LeaveEmbeddable::toDomain).toList(),
				new HospitalCalendar(holidays),
				weekdayPerShift, weekendPerShift);
	}

}
