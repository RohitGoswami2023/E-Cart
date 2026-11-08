package com.ecommerce.project.model;

/**
 * Role Domain Entity
 *
 * Why & Where it is used:
 *   Represents an authority role in PostgreSQL table `roles`.
 *   Stores the persistent representation of the AppRole enum (ROLE_USER, ROLE_SELLER, ROLE_ADMIN).
 *
 * Imports & Key Dependencies:
 *   - Jakarta Persistence: @Entity, @Table, @Enumerated(EnumType.STRING).
 *   - Lombok: @Data, @NoArgsConstructor, @AllArgsConstructor.
 *
 * Exports & Consumers:
 *   - RoleRepository, WebSecurityConfig, AuthServiceImpl, UserDetailsImpl.
 *
 * Project Impact:
 *   Provides the persistent bridge between security role enums and user account associations.
 */

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;

@Entity
@NoArgsConstructor
@AllArgsConstructor
@Data
@Table(name = "roles")
public class Role {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "role_id")
    private Integer roleId;

    @ToString.Exclude
    @Enumerated(EnumType.STRING)
    @Column(length = 20, name = "role_name")
    private AppRole roleName;

    public Role(AppRole roleName) {
        this.roleName = roleName;
    }
}
