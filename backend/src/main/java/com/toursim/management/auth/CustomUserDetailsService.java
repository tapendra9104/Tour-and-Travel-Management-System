package com.toursim.management.auth;

import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
public class CustomUserDetailsService implements UserDetailsService {

    private final AppUserRepository appUserRepository;

    public CustomUserDetailsService(AppUserRepository appUserRepository) {
        this.appUserRepository = appUserRepository;
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        String normalizedUsername = username == null ? "" : username.trim();
        AppUser appUser = appUserRepository.findByEmailIgnoreCase(normalizedUsername)
            .orElseThrow(() -> new UsernameNotFoundException("User not found"));

        return User.withUsername(appUser.getEmail())
            .password(appUser.getPasswordHash())
            .roles(appUser.getRole().name())
            .disabled(!appUser.isEnabled())
            .build();
    }
}
