package com.iitm.roster.repo;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

interface RosterJpaRepository extends JpaRepository<RosterEntity, Long> {

	Optional<RosterEntity> findByMonth(String month);

}
