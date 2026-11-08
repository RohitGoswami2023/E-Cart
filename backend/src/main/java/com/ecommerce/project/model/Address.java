package com.ecommerce.project.model;

/**
 * Address Domain Entity
 *
 * Why & Where it is used:
 *   Represents a physical shipping and billing address associated with a user in PostgreSQL table `addresses`.
 *
 * Imports & Key Dependencies:
 *   - Jakarta Persistence: @Entity, @Table, @Id, @GeneratedValue, @ManyToOne, @JoinColumn.
 *   - Jakarta Validation: @NotBlank, @Size constraints.
 *   - Lombok: @Data, @NoArgsConstructor, @AllArgsConstructor.
 *
 * Exports & Consumers:
 *   - AddressRepository, AddressServiceImpl, AddressController.
 *   - Order: Referenced as the shipping destination for completed purchases.
 *
 * Project Impact:
 *   Ensures structured address storage with field-level length validations (street, city, pincode).
 */

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "addresses")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Address {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long addressId;

    @NotBlank
    @Size(min = 5, message = "Street name must be atleast 5 characters")
    private String street;

    @NotBlank
    @Size(min = 5, message = "Building name must be atleast 5 characters")
    private String buildingName;

    @NotBlank
    @Size(min = 4, message = "City name must be atleast 4 characters")
    private String city;

    @NotBlank
    @Size(min = 2, message = "State name must be atleast 2 characters")
    private String state;

    @NotBlank
    @Size(min = 2, message = "Country name must be atleast 2 characters")
    private String country;

    @NotBlank
    @Size(min = 5, message = "Pincode must be atleast 5 characters")
    private String pincode;

    @ManyToOne
    @JoinColumn(name = "user_id")
    private User user;

    public Address(String street, String buildingName, String city, String state, String country, String pincode) {
        this.street = street;
        this.buildingName = buildingName;
        this.city = city;
        this.state = state;
        this.country = country;
        this.pincode = pincode;
    }
}
