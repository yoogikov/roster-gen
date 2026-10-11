package com.iitm.roster.repo;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

interface StaffJpaRepository extends JpaRepository<StaffEntity, String> {

	List<StaffEntity> findAllByOrderByIdAsc();

}
