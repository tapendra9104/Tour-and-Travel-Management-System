package com.toursim.management.booking.dto;

import java.time.LocalDateTime;

import com.toursim.management.booking.BookingActivity;

public record BookingActivityResponse(
    String actionType,
    String actorName,
    String actorRole,
    String previousStatus,
    String newStatus,
    String note,
    LocalDateTime createdAt
) {

    public static BookingActivityResponse from(BookingActivity activity) {
        return new BookingActivityResponse(
            activity.getActionType(),
            activity.getActorName(),
            activity.getActorRole(),
            activity.getPreviousStatus(),
            activity.getNewStatus(),
            activity.getNote(),
            activity.getCreatedAt()
        );
    }
}
