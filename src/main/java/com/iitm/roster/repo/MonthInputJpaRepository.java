package com.iitm.roster.repo;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

interface MonthInputJpaRepository extends JpaRepository<MonthInputEntity, Long> {

	Optional<MonthInputEntity> findByMonth(String month);

}
