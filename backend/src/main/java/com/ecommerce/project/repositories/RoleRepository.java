package com.ecommerce.project.repositories;

/**
 * Role Spring Data JPA Repository
 *
 * Why & Where it is used:
 *   Handles database operations for security roles in PostgreSQL table `roles`.
 *   Resolves Role entities by their AppRole enum identifier.
 *
 * Imports & Key Dependencies:
 *   - Domain Models: Role, AppRole (ROLE_USER, ROLE_SELLER, ROLE_ADMIN).
 *   - Spring Data JPA: JpaRepository.
 *
 * Exports & Consumers:
 *   - WebSecurityConfig: Used during initial bootstrap seeding to guarantee core roles exist.
 *   - AuthServiceImpl: Assigns roles to newly registered user accounts.
 *
 * Project Impact:
 *   Persistence layer for the platform's role-based access control (RBAC) system.
 */

import com.ecommerce.project.model.AppRole;
import com.ecommerce.project.model.Role;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface RoleRepository extends JpaRepository<Role, Long> {
    Optional<Role> findByRoleName(AppRole appRole);
}
