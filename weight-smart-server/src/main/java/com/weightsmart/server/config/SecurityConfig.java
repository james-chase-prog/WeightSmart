package com.weightsmart.server.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

/*
 * SecurityConfig
 * Configuration class for Spring Security.
 * Defines the "Security Filter Chain" which acts as the firewall/gateway for the API.
 * REQUEST -> [JWT Filter] -> [Authorization Rules] -> Controller
 *
 * Architecture Role:
 * This class is the central security definition for the application. It:
 * 1. Disables CSRF: Essential for stateless REST APIs (Mobile Clients) where Session Cookies are not used.
 * 2. Configures Authorization: Defines which endpoints are public (Registration) vs. private (Admin).
 * 3. Integrates JWT: Injects the custom {@code JwtAuthenticationFilter} before the standard login filter.
 * 4. CORS: Restricts cross-origin requests to allowed origins only.
 * 5. HTTPS: Conditionally enforces TLS in production via server.ssl.enabled property.
 *
 * Key Concepts & Documentation:
 * Spring Security Architecture: Visual explanation of the Filter Chain and how requests are intercepted.
 * <a href="https://docs.spring.io/spring-security/reference/servlet/architecture.html">Reference: Architecture</a>
 * Stateless Sessions: Why we disable JSESSIONID cookies for REST APIs.
 * <a href="https://www.baeldung.com/spring-security-session-management">Reference: Baeldung Session Management</a>
 * CSRF (Cross-Site Request Forgery): Why it is safe to disable this for non-browser clients (like Android).
 * <a href="https://docs.spring.io/spring-security/reference/features/exploits/csrf.html">Reference: CSRF Protection</a>
 * CORS (Cross-Origin Resource Sharing): Prevents unauthorized browser-based cross-origin API access.
 * <a href="https://docs.spring.io/spring-security/reference/servlet/integrations/cors.html">Reference: Spring CORS</a>
 *
 * @author James Chase
 * @version 3.0 (P5: CORS, HTTPS enforcement, refresh token endpoint)
 * @since 2026-01-14
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthFilter;
    private final AuthenticationProvider authenticationProvider;

    // Conditional HTTPS enforcement -- only in production
    @Value("${server.ssl.enabled:false}")
    private boolean sslEnabled;

    public SecurityConfig(JwtAuthenticationFilter jwtAuthFilter, AuthenticationProvider authenticationProvider) {
        this.jwtAuthFilter = jwtAuthFilter;
        this.authenticationProvider = authenticationProvider;
    }

    /**
     * Defines the security rules for HTTP requests.
     *
     * @param http The HttpSecurity object to configure.
     * @return The built SecurityFilterChain.
     * @throws Exception if configuration fails.
     */
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                // CORS configuration
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))

                // Disable CSRF (Not needed for stateless JWT APIs)
                .csrf(AbstractHttpConfigurer::disable)

                // Set Session Policy to STATELESS
                // This ensures Spring never creates a JSESSIONID cookie.
                // Every request must send the JWT token.
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))

                // Define URL Access Rules
                .authorizeHttpRequests(auth -> auth
                        // Public Endpoints (Login/Register/Search/Refresh/Error)
                        .requestMatchers("/api/auth/login").permitAll()
                        .requestMatchers("/api/auth/register").permitAll()
                        .requestMatchers("/api/auth/search-usernames").permitAll()
                        .requestMatchers("/api/auth/refresh").permitAll()
                        .requestMatchers("/error").permitAll()

                        // All other endpoints require a Token (includes /api/auth/update, /api/auth/me, /api/auth/validate, /api/auth/logout)
                        .anyRequest().authenticated()
                )
                .authenticationProvider(authenticationProvider)

                // Add the JWT Filter BEFORE the standard Username/Password filter
                .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);

        // HTTPS redirect in production (when SSL is enabled)
        if (sslEnabled) {
            http.requiresChannel(channel -> channel.anyRequest().requiresSecure());
        }

        return http.build();
    }

    /**
     * CORS Configuration Source.
     * Restricts cross-origin browser requests to allowed origins only.
     * Android native clients are not affected by CORS (browser-only mechanism).
     *
     * @return The CORS configuration source applied to /api/** endpoints.
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(List.of("http://localhost:3000")); // Add production origins as needed
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("Authorization", "Content-Type"));
        config.setAllowCredentials(true);
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", config);
        return source;
    }
}
