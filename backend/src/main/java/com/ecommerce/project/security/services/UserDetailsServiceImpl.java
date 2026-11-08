package com.ecommerce.project.security.services;

/**
 * UserDetails Service Implementation
 *
 * Why & Where it is used:
 *   Bridge between Spring Security's authentication subsystem and the PostgreSQL database.
 *   Implements UserDetailsService to resolve full user account entities and their GrantedAuthorities.
 *
 * Imports & Key Dependencies:
 *   - Domain: User entity.
 *   - Repository: UserRepository (retrieves record and associated roles by username).
 *   - Security: Spring Security UserDetailsService, UserDetailsImpl.
 *
 * Exports & Consumers:
 *   - WebSecurityConfig: Injected into DaoAuthenticationProvider.
 *   - AuthTokenFilter: Injected to reconstruct the authentication token context on incoming requests.
 *
 * Project Impact:
 *   Ensures account lookups are executed in transactional scope to load lazy-loaded role collections
 *   cleanly without triggering LazyInitializationException.
 */

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ecommerce.project.model.User;
import com.ecommerce.project.repositories.UserRepository;

@Service
public class UserDetailsServiceImpl implements UserDetailsService {
    @Autowired
    UserRepository userRepository;

    @Override
    @Transactional
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        User user = userRepository.findByUserName(username)
                .orElseThrow(() -> new UsernameNotFoundException("User Not Found with username: " + username));

        return UserDetailsImpl.build(user);
    }


}