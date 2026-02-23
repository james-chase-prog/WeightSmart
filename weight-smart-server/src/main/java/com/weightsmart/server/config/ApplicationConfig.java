package com.weightsmart.server.config;

import com.weightsmart.server.repository.UserRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/*
 * ApplicationConfig
 * Holds application-wide configuration beans.
 * Defines the Spring Security beans that power authentication:
 *  1) UserDetailsService: how to load users from the database by username
 *  2) AuthenticationProvider: how Spring verifies username/password (DAO auth)
 *  3) AuthenticationManager: entry point used by AuthController for login
 *  4) PasswordEncoder: BCrypt hashing + verification
 *
 * Architecture Role:
 * This class isolates the "Security Tools" (User Lookup, Password Hashing) from the "Security Rules" (Filter Chain).
 * This separation is critical to prevent Circular Dependency errors during Spring Boot startup.
 *
 * Key Concepts & Documentation:
 * UserDetailsService: The bridge that loads user-specific data from the database.
 * <a href="https://docs.spring.io/spring-security/reference/servlet/authentication/passwords/user-details-service.html">Reference: UserDetailsService</a>
 * DAO Authentication Provider: The specific implementation that retrieves user details from a UserDetailsService.
 * <a href="https://docs.spring.io/spring-security/reference/servlet/authentication/passwords/dao-authentication-provider.html">Reference: DaoAuthenticationProvider</a>
 * BCrypt: The hashing algorithm used to secure passwords.
 * <a href="https://en.wikipedia.org/wiki/Bcrypt">Reference: BCrypt Wikipedia</a>
 *
 * @author James Chase
 * @version 1.0
 * @since 2026-01-17
 */
@Configuration
public class ApplicationConfig {

    private final UserRepository userRepository;

    public ApplicationConfig(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    /**
     * The bridge between Spring Security and our Database.
     * It tells Spring how to look up a user by username.
     */
    @Bean
    public UserDetailsService userDetailsService() {
        return username -> userRepository.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("User not found"));
    }

    /**
     * The "Manager" that actually processes login attempts.
     * We expose this bean so our AuthController can use it.
     */
    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }

    /**
     * The "Provider" that connects the UserDetailsService and PasswordEncoder.
     * This is the engine that checks "Does password match hash?"
     */
    @Bean
    public AuthenticationProvider authenticationProvider() {
        // Constructor injection required for this specific Spring Security version
        DaoAuthenticationProvider authProvider = new DaoAuthenticationProvider(userDetailsService());
        authProvider.setPasswordEncoder(passwordEncoder());
        return authProvider;
    }

    /**
     * The Password Encoder (BCrypt).
     * Strength 10 (default) provides a balance between security and performance.
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}