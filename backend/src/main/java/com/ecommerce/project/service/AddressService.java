package com.ecommerce.project.service;

/**
 * Address Service Interface
 *
 * Why & Where it is used:
 *   Defines the business operations for user address persistence, retrieval, modification,
 *   and removal, ensuring all operations accept a User principal to enforce ownership verification.
 *
 * Imports & Key Dependencies:
 *   - Domain: User entity.
 *   - Payloads: AddressDTO.
 *
 * Exports & Consumers:
 *   - Implemented by AddressServiceImpl.
 *   - Injected into AddressController.
 *
 * Project Impact:
 *   Establishes the contract for address isolation across multiple tenant users.
 */

import com.ecommerce.project.model.User;
import com.ecommerce.project.payload.AddressDTO;

import java.util.List;

public interface AddressService {
    AddressDTO createAddress(AddressDTO addressDTO, User user);

    AddressDTO getAddressesById(Long addressId, User user);

    List<AddressDTO> getUserAddresses(User user);

    AddressDTO updateAddress(Long addressId, AddressDTO addressDTO, User user);

    String deleteAddress(Long addressId, User user);
}
