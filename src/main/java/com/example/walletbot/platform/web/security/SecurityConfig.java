package com.example.walletbot.platform.web.security;

import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;

    @Value("${cors.allowed-origins:http://localhost:3000}")
    private String allowedOrigins;

    private static final Set<String> LOOPBACK_HOSTS = Set.of("localhost", "127.0.0.1", "::1");

    static void validateOrigin(String origin) {
        URI uri;
        try {
            uri = new URI(origin);
        } catch (URISyntaxException e) {
            throw new IllegalStateException("Invalid CORS origin: " + origin);
        }
        String scheme = uri.getScheme();
        String host = uri.getHost();
        if (scheme == null || host == null) {
            throw new IllegalStateException("CORS origin must be absolute (scheme://host[:port]): " + origin);
        }
        if (uri.getPath() != null && !uri.getPath().isEmpty()) {
            throw new IllegalStateException("CORS origin must not contain a path: " + origin);
        }
        if (host.contains("*")) {
            throw new IllegalStateException("CORS origin must not contain wildcards: " + origin);
        }
        boolean loopback = LOOPBACK_HOSTS.contains(host.toLowerCase());
        if (!"https".equalsIgnoreCase(scheme) && !(loopback && "http".equalsIgnoreCase(scheme))) {
            throw new IllegalStateException(
                    "CORS origin must use https (http allowed only for loopback): " + origin);
        }
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .headers(headers -> headers
                        .contentSecurityPolicy(csp -> csp.policyDirectives("default-src 'none'"))
                        .frameOptions(frame -> frame.deny())
                        .contentTypeOptions(Customizer.withDefaults())
                        .referrerPolicy(rp -> rp.policy(ReferrerPolicyHeaderWriter.ReferrerPolicy.NO_REFERRER))
                        .httpStrictTransportSecurity(hsts -> hsts
                                .includeSubDomains(true)
                                .maxAgeInSeconds(31_536_000))
                )
                .exceptionHandling(ex -> ex.authenticationEntryPoint((request, response, authEx) -> {
                    response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                    response.setContentType("application/json");
                    response.getWriter().write("{\"message\":\"Unauthorized\",\"code\":\"UNAUTHORIZED\",\"status\":401}");
                }))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/miniapp/auth").permitAll()
                        .requestMatchers(org.springframework.http.HttpMethod.POST, "/api/miniapp/auth/refresh").permitAll()
                        .requestMatchers(org.springframework.http.HttpMethod.POST, "/api/miniapp/auth/2fa").permitAll()
                        .requestMatchers(org.springframework.http.HttpMethod.POST, "/api/miniapp/auth/2fa/resend").permitAll()
                        .requestMatchers(org.springframework.http.HttpMethod.POST, "/api/miniapp/security/pin/verify").permitAll()
                        .requestMatchers(org.springframework.http.HttpMethod.POST, "/api/miniapp/security/pin/reset/request-token").permitAll()
                        .requestMatchers(org.springframework.http.HttpMethod.POST, "/api/miniapp/security/pin/reset/confirm-token").permitAll()
                        .requestMatchers(org.springframework.http.HttpMethod.GET, "/api/miniapp/i18n").permitAll()
                        .requestMatchers(org.springframework.http.HttpMethod.POST, "/api/webhooks/alchemy/**").permitAll()
                        .requestMatchers(org.springframework.http.HttpMethod.POST, "/api/webhooks/helius").permitAll()
                        .requestMatchers(org.springframework.http.HttpMethod.POST, "/api/webhooks/ton").permitAll()
                        .requestMatchers("/api/miniapp/**").authenticated()
                        .requestMatchers("/api/admin/**").hasRole("ADMIN")
                        .requestMatchers("/api/internal/**").denyAll()
                        .requestMatchers("/actuator/health", "/actuator/health/**").permitAll()
                        .requestMatchers("/actuator/**").denyAll()
                        .anyRequest().permitAll()
                )
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        List<String> origins = Arrays.stream(allowedOrigins.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toList());
        if (origins.isEmpty()) {
            throw new IllegalStateException("cors.allowed-origins must not be empty");
        }
        origins.forEach(SecurityConfig::validateOrigin);

        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(origins);
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("Authorization", "Content-Type", "Idempotency-Key"));
        config.setAllowCredentials(true);
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", config);
        return source;
    }

}