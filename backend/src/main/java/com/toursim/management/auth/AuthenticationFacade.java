package com.toursim.management.auth;

import java.util.Optional;

import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

@Component
public class AuthenticationFacade {

    private final AppUserRepository appUserRepository;

    public AuthenticationFacade(AppUserRepository appUserRepository) {
        this.appUserRepository = appUserRepository;
    }

    public Optional<AppUser> currentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated() || authentication instanceof AnonymousAuthenticationToken) {
            return Optional.empty();
        }
        String email = authentication.getName();
        if (email == null || email.isBlank()) {
            return Optional.empty();
        }
        return appUserRepository.findByEmailIgnoreCase(email.trim());
    }

    public boolean isAdmin() {
        return currentUser()
            .map(appUser -> appUser.getRole() == UserRole.ADMIN)
            .orElse(false);
    }
}
