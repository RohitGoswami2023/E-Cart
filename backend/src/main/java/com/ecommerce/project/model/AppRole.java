package com.ecommerce.project.model;

/**
 * Application Security Role Enumeration
 *
 * Why & Where it is used:
 *   Enumerates the three foundational security roles recognized across the platform:
 *   ROLE_USER (standard buyer), ROLE_SELLER (merchant inventory vendor), and ROLE_ADMIN (platform administrator).
 *
 * Imports & Key Dependencies:
 *   - Standalone enum.
 *
 * Exports & Consumers:
 *   - Role: Persisted to table `roles` via roleName.
 *   - WebSecurityConfig: Referenced in Spring Security `.hasRole(...)` expressions.
 *   - UserDetailsImpl: Converted into GrantedAuthority strings for authorization checks.
 *
 * Project Impact:
 *   Forms the bedrock of role-based access control (RBAC), preventing privilege confusion.
 */

public enum AppRole {
    ROLE_USER,
    ROLE_SELLER,
    ROLE_ADMIN
}
