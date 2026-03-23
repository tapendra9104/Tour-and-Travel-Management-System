package com.toursim.management.dashboard;

import java.time.LocalDateTime;

import com.toursim.management.notification.Notification;

public record NotificationResponse(
    String category,
    String subject,
    String message,
    String severity,
    String status,
    LocalDateTime createdAt
) {

    public static NotificationResponse from(Notification notification) {
        return new NotificationResponse(
            notification.getCategory().name().toLowerCase(),
            notification.getSubject(),
            notification.getMessage(),
            "info",
            notification.getStatus().name().toLowerCase(),
            notification.getCreatedAt()
        );
    }

    public static NotificationResponse operational(String category, String subject, String message, String severity, LocalDateTime createdAt) {
        return new NotificationResponse(category, subject, message, severity, "generated", createdAt);
    }
}
