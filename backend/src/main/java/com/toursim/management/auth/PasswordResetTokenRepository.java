package com.toursim.management.auth;

import java.time.LocalDateTime;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetToken, Long> {

    Optional<PasswordResetToken> findByToken(String token);

    /** Deletes all tokens for a user (clean-up before issuing a new one). */
    void deleteAllByUser(AppUser user);

    /** Purges expired tokens - can be called periodically to keep the table clean. */
    @Modifying
    @Query("delete from PasswordResetToken t where t.expiresAt < :now")
    void deleteExpiredTokens(@Param("now") LocalDateTime now);
}
