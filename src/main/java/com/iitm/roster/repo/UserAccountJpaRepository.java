package com.iitm.roster.repo;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

interface UserAccountJpaRepository extends JpaRepository<UserAccountEntity, String> {

	Optional<UserAccountEntity> findByUsername(String username);

	boolean existsByRole(String role);

	List<UserAccountEntity> findAllByOrderByUsernameAsc();

	Optional<UserAccountEntity> findByStaffId(String staffId);

}
