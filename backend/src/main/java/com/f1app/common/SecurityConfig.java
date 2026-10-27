package com.f1app.common;

import java.util.List;

import com.f1app.auth.JwtAuthenticationFilter;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

/**
 * Phase 5: the permitAll() from Phase 1 becomes real rules.
 *
 * CSRF stays disabled, and now for a concrete reason rather than convenience:
 * CSRF attacks work because browsers attach cookies automatically. A JWT in an
 * Authorization header is attached by application code, so a cross-site form
 * post cannot carry it. If this ever moves to cookie-based auth, CSRF
 * protection has to come back on.
 *
 * Sessions stay STATELESS, which is what lets the backend scale horizontally
 * without sticky sessions or a shared session store.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Value("${app.cors.allowed-origins}")
    private List<String> allowedOrigins;

    private final JwtAuthenticationFilter jwtFilter;

    public SecurityConfig(JwtAuthenticationFilter jwtFilter) {
        this.jwtFilter = jwtFilter;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))
            .csrf(csrf -> csrf.disable())
            .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                    // CORS preflight carries no credentials and must never 401
                    .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()

                    // Public: health, F1 reference data, the chatbot, and the
                    // two endpoints you need before you have a token
                    .requestMatchers("/api/health", "/actuator/**").permitAll()
                    .requestMatchers("/api/auth/register", "/api/auth/login").permitAll()
                    .requestMatchers(HttpMethod.GET,
                            "/api/overview", "/api/seasons", "/api/races/**",
                            "/api/drivers/**", "/api/constructors/**",
                            "/api/standings/**", "/api/ping").permitAll()
                    .requestMatchers(HttpMethod.POST, "/api/chat").permitAll()
                    .requestMatchers("/api/health", "/actuator/**").permitAll()
                    .requestMatchers("/error").permitAll()

                    // Ingestion is an operator tool, not a user feature
                    .requestMatchers("/api/admin/**").hasRole("ADMIN")

                    // Everything else - /api/auth/me, /api/favorites/** - needs a token
                    .anyRequest().authenticated())

            // Without this, an unauthenticated request to a protected endpoint
            // gets a 403 from Spring's default handler. The frontend needs 401
            // to distinguish "log in" from "you may not do that".
            .exceptionHandling(ex -> ex
                    .authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)))

            .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(allowedOrigins);
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}
