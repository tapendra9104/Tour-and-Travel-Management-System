package com.toursim.management.config;

import java.time.Duration;

import javax.sql.DataSource;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.rememberme.JdbcTokenRepositoryImpl;
import org.springframework.security.web.authentication.rememberme.PersistentTokenRepository;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    /**
     * All public (unauthenticated) URL patterns. Explicit allowlist - every other
     * route requires authentication by default (anyRequest().authenticated()).
     */
    private static final String[] PUBLIC_PAGES = {
        "/", "/tours", "/tours/**", "/destinations", "/about", "/contact",
        "/login", "/register", "/forgot-password", "/reset-password",
        "/health", "/error", "/robots.txt", "/sitemap.xml"
    };

    private static final String[] PUBLIC_ASSETS = {
        "/css/**", "/js/**", "/icon*", "/apple-icon.png",
        // Swagger UI + OpenAPI spec (toggle via SPRINGDOC_ENABLED env var)
        "/swagger-ui.html", "/swagger-ui/**", "/v3/api-docs/**"
    };

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http,
                                                   PersistentTokenRepository tokenRepository) throws Exception {
        http
            // -- Authorization -----------------------------------------------------
            .authorizeHttpRequests(auth -> auth
                .requestMatchers(PUBLIC_PAGES).permitAll()
                .requestMatchers(PUBLIC_ASSETS).permitAll()
                // Public booking / contact / newsletter APIs
                .requestMatchers(HttpMethod.POST,
                    "/api/bookings", "/api/bookings/lookup",
                    "/api/contact", "/api/newsletter/subscribe").permitAll()
                // Guest self-service (cancel, reschedule, preferences) - verified by reference+email
                .requestMatchers(HttpMethod.POST,
                    "/api/bookings/*/cancel", "/api/bookings/*/reschedule",
                    "/api/bookings/*/preferences", "/api/bookings/*/activity",
                    "/api/bookings/*/payments").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/dashboard").permitAll()
                // Admin-only
                .requestMatchers("/api/admin/**").hasRole("ADMIN")
                .requestMatchers(HttpMethod.PATCH, "/api/bookings/*").hasRole("ADMIN")
                // Authenticated users only
                .requestMatchers(HttpMethod.GET, "/api/bookings").authenticated()
                .requestMatchers(HttpMethod.GET, "/api/notifications").authenticated()
                // Secure by default - everything not explicitly permitted requires auth
                .anyRequest().authenticated()
            )

            // -- Form Login ---------------------------------------------------------
            .formLogin(form -> form
                .loginPage("/login")
                .successHandler((request, response, authentication) -> {
                    boolean admin = authentication.getAuthorities().stream()
                        .anyMatch(authority -> "ROLE_ADMIN".equals(authority.getAuthority()));
                    response.sendRedirect(admin ? "/admin" : "/dashboard");
                })
                .failureUrl("/login?error")
                .permitAll()
            )

            // -- Logout ------------------------------------------------------------
            .logout(logout -> logout
                .logoutSuccessUrl("/login?logout=1")
                .invalidateHttpSession(true)
                .clearAuthentication(true)
                .deleteCookies("JSESSIONID", "remember-me")
                .permitAll()
            )

            // -- Session -----------------------------------------------------------
            .sessionManagement(session -> session
                .invalidSessionUrl("/login")
                .sessionFixation(fix -> fix.migrateSession())
            )

            // -- CSRF --------------------------------------------------------------
            .csrf(csrf -> csrf
                .csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
            )

            // -- Remember Me (DB-backed - survives restarts, individually revocable) -
            .rememberMe(rememberMe -> rememberMe
                .rememberMeParameter("remember-me")
                .tokenValiditySeconds((int) Duration.ofDays(14).getSeconds())
                .tokenRepository(tokenRepository)
            )

            // -- Security Headers --------------------------------------------------
            .headers(headers -> headers
                // Deny embedding in iframes (clickjacking)
                .frameOptions(options -> options.deny())
                // Prevent MIME-type sniffing
                .contentTypeOptions(Customizer.withDefaults())
                // Content Security Policy
                .contentSecurityPolicy(csp -> csp.policyDirectives(
                    "default-src 'self'; " +
                    "script-src 'self' 'unsafe-inline'; " +
                    "style-src 'self' 'unsafe-inline' https://fonts.googleapis.com; " +
                    "font-src 'self' https://fonts.gstatic.com; " +
                    "img-src 'self' data: https://images.unsplash.com https://*.unsplash.com; " +
                    "connect-src 'self'; " +
                    "frame-ancestors 'none';"
                ))
            );

        return http.build();
    }

    /**
     * DB-backed remember-me token store (requires V7 migration: persistent_logins table).
     * Tokens are invalidated on logout and survive application restarts.
     */
    @Bean
    public PersistentTokenRepository persistentTokenRepository(DataSource dataSource) {
        JdbcTokenRepositoryImpl repo = new JdbcTokenRepositoryImpl();
        repo.setDataSource(dataSource);
        return repo;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
