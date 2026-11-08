package com.ecommerce.project.controller;

/**
 * Address REST Controller
 *
 * Why & Where it is used:
 *   Exposes REST endpoints for authenticated customer shipping address management
 *   (creating new addresses, fetching saved addresses, updating records, and deleting old ones).
 *
 * Imports & Key Dependencies:
 *   - Service: AddressService (executes CRUD and verifies user ownership).
 *   - Security: AuthUtil (resolves currently logged-in User entity).
 *   - Payloads: AddressDTO with Jakarta validation annotations.
 *
 * Exports & Consumers:
 *   - Frontend Checkout & Profile: Consumed during checkout destination selection and profile management.
 *
 * Project Impact:
 *   Ensures customers can only manage their own physical address records and prevents cross-user leakage.
 */

import com.ecommerce.project.model.User;
import com.ecommerce.project.payload.AddressDTO;
import com.ecommerce.project.service.AddressService;
import com.ecommerce.project.util.AuthUtil;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api")
public class AddressController {

    @Autowired
    AuthUtil authUtil;

    @Autowired
    AddressService addressService;

    @PostMapping("/addresses")
    public ResponseEntity<AddressDTO> createAddress(@Valid @RequestBody AddressDTO addressDTO) {
        User user = authUtil.loggedInUser();
        AddressDTO savedAddressDTO = addressService.createAddress(addressDTO, user);
        return new ResponseEntity<>(savedAddressDTO, HttpStatus.CREATED);
    }

    @GetMapping("/addresses")
    public ResponseEntity<List<AddressDTO>> getAddresses() {
        User user = authUtil.loggedInUser();
        List<AddressDTO> addressList = addressService.getUserAddresses(user);
        return new ResponseEntity<>(addressList, HttpStatus.OK);
    }

    @GetMapping("/addresses/{addressId}")
    public ResponseEntity<AddressDTO> getAddressById(@PathVariable Long addressId) {
        User user = authUtil.loggedInUser();
        AddressDTO addressDTO = addressService.getAddressesById(addressId, user);
        return new ResponseEntity<>(addressDTO, HttpStatus.OK);
    }

    @GetMapping("/users/addresses")
    public ResponseEntity<List<AddressDTO>> getUserAddresses() {
        User user = authUtil.loggedInUser();
        List<AddressDTO> addressList = addressService.getUserAddresses(user);
        return new ResponseEntity<>(addressList, HttpStatus.OK);
    }

    @PutMapping("/addresses/{addressId}")
    public ResponseEntity<AddressDTO> updateAddress(@PathVariable Long addressId,
                                                    @Valid @RequestBody AddressDTO addressDTO) {
        User user = authUtil.loggedInUser();
        AddressDTO updatedAddress = addressService.updateAddress(addressId, addressDTO, user);
        return new ResponseEntity<>(updatedAddress, HttpStatus.OK);
    }

    @DeleteMapping("/addresses/{addressId}")
    public ResponseEntity<String> deleteAddress(@PathVariable Long addressId) {
        User user = authUtil.loggedInUser();
        String status = addressService.deleteAddress(addressId, user);
        return new ResponseEntity<>(status, HttpStatus.OK);
    }
}
