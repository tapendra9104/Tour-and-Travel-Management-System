package com.toursim.management.notification;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

    List<Notification> findTop8ByRecipientUserIdOrderByCreatedAtDesc(Long recipientUserId);

    List<Notification> findTop10ByAdminVisibleTrueOrderByCreatedAtDesc();

    boolean existsByCategoryAndRelatedBookingIdAndRecipientEmailIgnoreCaseAndCreatedAtAfter(
        NotificationCategory category,
        Long relatedBookingId,
        String recipientEmail,
        LocalDateTime createdAt
    );
}
