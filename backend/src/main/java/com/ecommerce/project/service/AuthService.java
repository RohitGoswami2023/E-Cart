package com.ecommerce.project.service;

/**
 * Authentication Service Interface
 *
 * Why & Where it is used:
 *   Specifies the contract for user sign-in verification, self-service registration,
 *   token cookie issuance/revocation, session detail extraction, and seller pagination.
 *
 * Imports & Key Dependencies:
 *   - Security Requests/Responses: LoginRequest, SignupRequest, UserInfoResponse, MessageResponse.
 *   - Spring Security: Authentication.
 *   - Spring Web: ResponseCookie for HTTP cookie operations.
 *
 * Exports & Consumers:
 *   - Implemented by AuthServiceImpl.
 *   - Injected into AuthController.
 *
 * Project Impact:
 *   Core contract defining all authentication and credential lifecycle operations.
 */

import com.ecommerce.project.payload.AuthenticationResult;
import com.ecommerce.project.payload.UserResponse;
import com.ecommerce.project.security.request.LoginRequest;
import com.ecommerce.project.security.request.SignupRequest;
import com.ecommerce.project.security.response.MessageResponse;
import com.ecommerce.project.security.response.UserInfoResponse;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;

public interface AuthService {

    AuthenticationResult login(LoginRequest loginRequest);

    ResponseEntity<MessageResponse> register(SignupRequest signUpRequest);

    UserInfoResponse getCurrentUserDetails(Authentication authentication);

    ResponseCookie logoutUser();

    UserResponse getAllSellers(Pageable pageable);
}
