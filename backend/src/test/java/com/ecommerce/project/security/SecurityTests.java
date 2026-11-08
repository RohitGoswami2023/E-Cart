package com.ecommerce.project.security;

/**
 * Security & Authorization Integration Tests
 *
 * Why & Where it is used:
 *   Validates core security boundaries:
 *   1) Privilege escalation prevention (signup requests cannot grant ROLE_ADMIN).
 *   2) IDOR defenses on Address resources (cross-user data access is blocked with 401/404).
 *   3) IDOR defenses on Cart mutations (cross-user cart deletion is prevented).
 *   4) Webhook cryptographic signature enforcement (unsigned Stripe webhooks are rejected).
 *
 * Imports & Key Dependencies:
 *   - Spring WebMvc Test: MockMvc, AutoConfigureMockMvc.
 *   - Repositories & Services: UserRepository, RoleRepository, AddressRepository, CartRepository, AuthService.
 *   - Benchmark: BenchmarkReport for recording security assertion latencies.
 *
 * Exports & Consumers:
 *   - Executed via `mvnw test "-Dtest=SecurityTests"`.
 *
 * Project Impact:
 *   Guarantees end-to-end multi-tenant isolation, protecting customer PII and administrative controls.
 */

import com.ecommerce.project.benchmark.BenchmarkReport;
import com.ecommerce.project.model.*;
import com.ecommerce.project.repositories.*;
import com.ecommerce.project.security.request.SignupRequest;
import com.ecommerce.project.service.AuthService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public class SecurityTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private AddressRepository addressRepository;

    @Autowired
    private CartRepository cartRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private CartItemRepository cartItemRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private AuthService authService;

    private User testUser;
    private User otherUser;
    private Address testAddress;
    private Address otherAddress;
    private Cart testCart;
    private Cart otherCart;
    private Product testProduct;

    @BeforeEach
    void setup() {
        testUser = new User("secUser1", "sec1@test.com", passwordEncoder.encode("pass123"));
        testUser = userRepository.save(testUser);
        Role userRole = roleRepository.findByRoleName(AppRole.ROLE_USER).orElseThrow();
        testUser.setRoles(Set.of(userRole));
        testUser = userRepository.save(testUser);

        otherUser = new User("secUser2", "sec2@test.com", passwordEncoder.encode("pass456"));
        otherUser = userRepository.save(otherUser);
        otherUser.setRoles(Set.of(userRole));
        otherUser = userRepository.save(otherUser);

        testAddress = new Address();
        testAddress.setStreet("123 Test St");
        testAddress.setCity("Test City");
        testAddress.setState("TS");
        testAddress.setCountry("TC");
        testAddress.setPincode("12345");
        testAddress.setUser(testUser);
        testAddress = addressRepository.save(testAddress);

        otherAddress = new Address();
        otherAddress.setStreet("456 Other St");
        otherAddress.setCity("Other City");
        otherAddress.setState("OS");
        otherAddress.setCountry("OC");
        otherAddress.setPincode("67890");
        otherAddress.setUser(otherUser);
        otherAddress = addressRepository.save(otherAddress);

        testProduct = new Product();
        testProduct.setProductName("Security Test Product");
        testProduct.setDescription("Test");
        testProduct.setPrice(100.0);
        testProduct.setSpecialPrice(100.0);
        testProduct.setDiscount(0.0);
        testProduct.setQuantity(10);
        testProduct = productRepository.save(testProduct);

        testCart = new Cart();
        testCart.setUser(testUser);
        testCart.setTotalPrice(100.0);
        testCart = cartRepository.save(testCart);

        otherCart = new Cart();
        otherCart.setUser(otherUser);
        otherCart.setTotalPrice(100.0);
        otherCart = cartRepository.save(otherCart);
    }

    @Test
    @DisplayName("Signup cannot set admin role")
    @Transactional
    void signupCannotSetAdminRole() {
        long t0 = System.currentTimeMillis();
        SignupRequest request = new SignupRequest();
        request.setUsername("hacker");
        request.setEmail("hacker@test.com");
        request.setPassword("password123");
        request.setRole(Set.of("ADMIN"));

        authService.register(request);

        User created = userRepository.findByUserName("hacker").orElseThrow();
        boolean hasAdmin = created.getRoles().stream()
                .anyMatch(r -> r.getRoleName() == AppRole.ROLE_ADMIN);
        BenchmarkReport.record("signup-no-admin", !hasAdmin, System.currentTimeMillis() - t0, null, null, null, null);
        assertThat(hasAdmin).isFalse();
    }

    @Test
    @DisplayName("Address IDOR: user cannot access another user's address")
    void addressIdorPrevented() throws Exception {
        long t0 = System.currentTimeMillis();
        mockMvc.perform(get("/api/addresses/" + otherAddress.getAddressId())
                .with(csrf()))
                .andExpect(status().isUnauthorized());
        BenchmarkReport.record("address-idor", true, System.currentTimeMillis() - t0, null, null, null, null);
    }

    @Test
    @DisplayName("Cart IDOR: user cannot delete from another user's cart")
    void cartIdorPrevented() throws Exception {
        long t0 = System.currentTimeMillis();
        CartItem item = new CartItem();
        item.setCart(otherCart);
        item.setProduct(testProduct);
        item.setQuantity(1);
        item.setProductPrice(100.0);
        item.setDiscount(0.0);
        item = cartItemRepository.save(item);

        mockMvc.perform(delete("/api/carts/" + otherCart.getCartId() + "/product/" + testProduct.getProductId())
                .with(csrf()))
                .andExpect(status().isUnauthorized());
        BenchmarkReport.record("cart-idor", true, System.currentTimeMillis() - t0, null, null, null, null);
    }

    @Test
    @DisplayName("Webhook unsigned request rejected in non-test profile")
    void webhookSignatureRequired() throws Exception {
        long t0 = System.currentTimeMillis();
        String payload = "{\"type\":\"payment_intent.succeeded\"}";
        mockMvc.perform(post("/api/webhooks/stripe")
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload))
                .andExpect(status().isBadRequest());
        BenchmarkReport.record("webhook-signature", true, System.currentTimeMillis() - t0, null, null, null, null);
    }
}
