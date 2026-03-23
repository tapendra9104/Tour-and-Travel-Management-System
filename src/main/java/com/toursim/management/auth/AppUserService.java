package com.toursim.management.auth;

import java.time.LocalDateTime;
import java.util.Optional;

import org.springframework.http.HttpStatus;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AppUserService {

    private final AppUserRepository appUserRepository;
    private final PasswordEncoder passwordEncoder;

    public AppUserService(AppUserRepository appUserRepository, PasswordEncoder passwordEncoder) {
        this.appUserRepository = appUserRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional(readOnly = true)
    public Optional<AppUser> findByEmail(String email) {
        if (email == null || email.isBlank()) {
            return Optional.empty();
        }
        return appUserRepository.findByEmailIgnoreCase(email.trim());
    }

    @Transactional(readOnly = true)
    public Optional<AppUser> findById(Long id) {
        if (id == null) {
            return Optional.empty();
        }
        return appUserRepository.findById(id);
    }

    @Transactional
    public AppUser registerUser(RegistrationRequest request) {
        return registerUser(request.email(), request.password(), request.fullName(), request.phone(), UserRole.USER);
    }

    @Transactional
    public AppUser registerUser(String email, String rawPassword, String fullName, String phone, UserRole role) {
        String normalizedEmail = normalizeEmail(email);
        if (appUserRepository.existsByEmailIgnoreCase(normalizedEmail)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "An account with this email already exists");
        }

        AppUser appUser = new AppUser();
        appUser.setEmail(normalizedEmail);
        appUser.setPasswordHash(passwordEncoder.encode(rawPassword));
        appUser.setFullName(fullName == null || fullName.isBlank() ? normalizedEmail : fullName.trim());
        appUser.setPhone(phone == null || phone.isBlank() ? null : phone.trim());
        appUser.setRole(role);
        appUser.setEnabled(true);
        try {
            return appUserRepository.save(appUser);
        } catch (DataIntegrityViolationException exception) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "An account with this email already exists", exception);
        }
    }

    @Transactional
    public void syncProfile(AppUser appUser, String fullName, String phone) {
        boolean changed = false;
        if (fullName != null && !fullName.isBlank() && !fullName.trim().equals(appUser.getFullName())) {
            appUser.setFullName(fullName.trim());
            changed = true;
        }
        if (phone != null && !phone.isBlank() && !phone.trim().equals(appUser.getPhone())) {
            appUser.setPhone(phone.trim());
            changed = true;
        }
        if (changed) {
            appUserRepository.save(appUser);
        }
    }

    @Transactional
    public void markLastLogin(String email) {
        findByEmail(email).ifPresent(appUser -> {
            appUser.setLastLoginAt(LocalDateTime.now());
            appUserRepository.save(appUser);
        });
    }

    @Transactional
    public void ensureSeedUser(String email, String rawPassword, String fullName, String phone, UserRole role) {
        if (appUserRepository.existsByEmailIgnoreCase(email)) {
            return;
        }
        registerUser(email, rawPassword, fullName, phone, role);
    }

    private String normalizeEmail(String email) {
        return email == null ? "" : email.trim().toLowerCase();
    }
}
