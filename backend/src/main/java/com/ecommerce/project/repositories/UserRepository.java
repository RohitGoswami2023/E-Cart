package com.ecommerce.project.repositories;

/**
 * User Spring Data JPA Repository
 *
 * Why & Where it is used:
 *   Handles database operations for customer, seller, and administrator accounts in table `users`.
 *   Supports lookups by username or email, existence checks, and paginated queries by assigned role.
 *
 * Imports & Key Dependencies:
 *   - Domain Models: User, AppRole.
 *   - Spring Data JPA: JpaRepository, Page, Pageable, @Query, @Param.
 *
 * Exports & Consumers:
 *   - UserDetailsServiceImpl: Used by Spring Security to load user principals during login.
 *   - AuthServiceImpl: Performs uniqueness checks during registration and lists seller accounts.
 *   - StripeServiceImpl: Reads and caches Stripe Customer IDs on user accounts.
 *
 * Project Impact:
 *   The primary account persistence layer; enforces username and email uniqueness constraints across 1M+ rows.
 */

import com.ecommerce.project.model.AppRole;
import com.ecommerce.project.model.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByUserName(String username);

    Optional<User> findByEmail(String email);

    Boolean existsByUserName(String username);

    Boolean existsByEmail(String email);

    @Query("SELECT u FROM User u JOIN u.roles r WHERE r.roleName = :role")
    Page<User> findByRoleName(@Param("role") AppRole role, Pageable pageable);

}
