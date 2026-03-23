package com.toursim.management.config;

import java.time.Duration;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .authorizeHttpRequests(authorize -> authorize
                .requestMatchers(
                    "/",
                    "/tours",
                    "/tours/**",
                    "/destinations",
                    "/about",
                    "/contact",
                    "/dashboard",
                    "/login",
                    "/register",
                    "/error",
                    "/css/**",
                    "/js/**",
                    "/icon*",
                    "/apple-icon.png"
                ).permitAll()
                .requestMatchers(HttpMethod.POST, "/api/bookings", "/api/bookings/lookup", "/api/contact").permitAll()
                .requestMatchers(
                    HttpMethod.POST,
                    "/api/bookings/*/cancel",
                    "/api/bookings/*/reschedule",
                    "/api/bookings/*/preferences",
                    "/api/bookings/*/activity",
                    "/api/bookings/*/payments"
                ).permitAll()
                .requestMatchers(HttpMethod.GET, "/api/dashboard").permitAll()
                .requestMatchers("/api/admin/**").hasRole("ADMIN")
                .requestMatchers(HttpMethod.PATCH, "/api/bookings/*").hasRole("ADMIN")
                .requestMatchers(HttpMethod.GET, "/api/bookings").authenticated()
                .requestMatchers(HttpMethod.GET, "/api/notifications").authenticated()
                .anyRequest().permitAll()
            )
            .formLogin(form -> form
                .loginPage("/login")
                .defaultSuccessUrl("/dashboard", true)
                .failureUrl("/login?error")
                .permitAll()
            )
            .logout(logout -> logout
                .logoutSuccessUrl("/login?logout=1")
                .invalidateHttpSession(true)
                .clearAuthentication(true)
                .deleteCookies("JSESSIONID", "remember-me")
                .permitAll()
            )
            .sessionManagement(session -> session
                .invalidSessionUrl("/login")
                .sessionFixation(fixation -> fixation.migrateSession())
            )
            .csrf(csrf -> csrf.csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse()))
            .rememberMe(rememberMe -> rememberMe
                .rememberMeParameter("remember-me")
                .tokenValiditySeconds((int) Duration.ofDays(14).getSeconds())
            );

        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
