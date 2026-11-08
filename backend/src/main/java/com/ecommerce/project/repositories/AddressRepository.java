package com.ecommerce.project.repositories;

/**
 * Address Spring Data JPA Repository
 *
 * Why & Where it is used:
 *   Handles database CRUD operations for the Address entity in PostgreSQL table `addresses`.
 *
 * Imports & Key Dependencies:
 *   - Domain: Address entity.
 *   - Spring Data JPA: JpaRepository (provides built-in CRUD, paging, and sorting).
 *
 * Exports & Consumers:
 *   - AddressServiceImpl: Injected to persist, query, and remove user shipping destinations.
 *   - SecurityTests: Used in integration test fixtures.
 *
 * Project Impact:
 *   Serves as the persistence layer for physical delivery addresses.
 */

import com.ecommerce.project.model.Address;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AddressRepository extends JpaRepository<Address, Long> {
}
