package com.toursim.management.auth;

import java.security.SecureRandom;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HexFormat;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.toursim.management.notification.NotificationCategory;
import com.toursim.management.notification.NotificationService;

@Service
public class PasswordResetService {

    private static final int TOKEN_EXPIRY_MINUTES = 60;
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final AppUserRepository appUserRepository;
    private final PasswordResetTokenRepository tokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final NotificationService notificationService;
    private final String baseUrl;

    public PasswordResetService(
        AppUserRepository appUserRepository,
        PasswordResetTokenRepository tokenRepository,
        PasswordEncoder passwordEncoder,
        NotificationService notificationService,
        @Value("${app.notifications.base-url:http://localhost:8080}") String baseUrl
    ) {
        this.appUserRepository = appUserRepository;
        this.tokenRepository = tokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.notificationService = notificationService;
        this.baseUrl = normalizeBaseUrl(baseUrl);
    }

    /**
     * Initiates a password reset. Always returns successfully (even if email not found)
     * to prevent email enumeration attacks.
     */
    @Transactional
    public void requestReset(String email) {
        if (email == null || email.isBlank()) {
            return;
        }
        appUserRepository.findByEmailIgnoreCase(email.trim()).ifPresent(user -> {
            // Invalidate any existing tokens for this user
            tokenRepository.deleteAllByUser(user);

            // Generate a secure URL-safe token
            byte[] bytes = new byte[36];
            SECURE_RANDOM.nextBytes(bytes);
            String rawToken = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);

            PasswordResetToken resetToken = new PasswordResetToken();
            resetToken.setUser(user);
            resetToken.setToken(hashToken(rawToken));
            resetToken.setExpiresAt(LocalDateTime.now().plusMinutes(TOKEN_EXPIRY_MINUTES));
            tokenRepository.save(resetToken);

            String resetLink = baseUrl + "/reset-password?token=" + rawToken;
            String message = "Hi " + user.getFullName() + ",\n\n"
                + "We received a request to reset your Wanderlust Travels password.\n\n"
                + "Click the link below to set a new password (valid for " + TOKEN_EXPIRY_MINUTES + " minutes):\n"
                + resetLink + "\n\n"
                + "If you did not request this, you can safely ignore this email.\n\n"
                + "Wanderlust Travels";

            notificationService.notifyUser(
                user,
                NotificationCategory.PASSWORD_RESET_REQUESTED,
                "Reset your Wanderlust password",
                message,
                null,
                null
            );
        });
    }

    /**
     * Validates the token and resets the password.
     *
     * @throws ResponseStatusException 400 if token is invalid/expired/used
     */
    @Transactional
    public void resetPassword(String rawToken, String newPassword) {
        if (rawToken == null || rawToken.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "This reset link is invalid or has already been used.");
        }
        PasswordResetToken resetToken = tokenRepository.findByToken(hashToken(rawToken))
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "This reset link is invalid or has already been used."));

        if (!resetToken.isValid()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "This reset link has expired. Please request a new one.");
        }

        AppUser user = resetToken.getUser();
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        appUserRepository.save(user);

        // Mark token as used (single-use enforcement)
        resetToken.setUsedAt(LocalDateTime.now());
        tokenRepository.save(resetToken);

        notificationService.notifyUser(
            user,
            NotificationCategory.PASSWORD_RESET_COMPLETED,
            "Your Wanderlust password was changed",
            "Hi " + user.getFullName() + ",\n\n"
                + "Your password was successfully reset. If you did not make this change, "
                + "please contact support immediately.\n\nWanderlust Travels",
            null,
            null
        );
    }

    /** Validates a token without consuming it for the reset-password page. */
    @Transactional(readOnly = true)
    public boolean isValidToken(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            return false;
        }
        return tokenRepository.findByToken(hashToken(rawToken))
            .map(PasswordResetToken::isValid)
            .orElse(false);
    }

    private String hashToken(String rawToken) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                .digest(rawToken.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available", exception);
        }
    }

    private String normalizeBaseUrl(String configuredBaseUrl) {
        String resolved = configuredBaseUrl == null || configuredBaseUrl.isBlank()
            ? "http://localhost:8080"
            : configuredBaseUrl.trim();
        return resolved.endsWith("/") ? resolved.substring(0, resolved.length() - 1) : resolved;
    }
}
