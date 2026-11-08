package com.ecommerce.project.security.jwt;

/**
 * JWT Authentication Entry Point
 *
 * Why & Where it is used:
 *   Acts as the custom failure handler invoked by Spring Security whenever an unauthenticated
 *   client attempts to access a protected API resource.
 *
 * Imports & Key Dependencies:
 *   - Jackson: ObjectMapper (formats standardized JSON error responses).
 *   - Servlet API: HttpServletRequest, HttpServletResponse.
 *   - Spring Security: AuthenticationEntryPoint, AuthenticationException.
 *
 * Exports & Consumers:
 *   - WebSecurityConfig: Registered via `.exceptionHandling(exception -> exception.authenticationEntryPoint(...))`.
 *
 * Project Impact:
 *   Prevents Spring Security from returning raw HTML login pages or generic container error pages,
 *   returning clean, machine-readable HTTP 401 JSON error bodies to frontend consumers.
 */

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

@Component
public class AuthEntryPointJwt implements AuthenticationEntryPoint {

    private static final Logger logger = LoggerFactory.getLogger(AuthEntryPointJwt.class);

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response, AuthenticationException authException)
            throws IOException, ServletException {
        logger.error("Unauthorized error: {}", authException.getMessage());

        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);

        final Map<String, Object> body = new HashMap<>();
        body.put("status", HttpServletResponse.SC_UNAUTHORIZED);
        body.put("error", "Unauthorized");
        body.put("message", authException.getMessage());
        body.put("path", request.getServletPath());

        final ObjectMapper mapper = new ObjectMapper();
        mapper.writeValue(response.getOutputStream(), body);
    }

}

