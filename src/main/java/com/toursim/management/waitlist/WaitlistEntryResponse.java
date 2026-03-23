package com.toursim.management.waitlist;

import java.time.LocalDate;
import java.time.LocalDateTime;

public record WaitlistEntryResponse(
    Long id,
    String waitlistReference,
    String tourId,
    String tourTitle,
    String customerName,
    String email,
    int guests,
    LocalDate travelDate,
    String status,
    String priority,
    Long bookingId,
    LocalDateTime createdAt
) {

    public static WaitlistEntryResponse from(WaitlistEntry waitlistEntry, String tourTitle, String priority) {
        return new WaitlistEntryResponse(
            waitlistEntry.getId(),
            waitlistEntry.getWaitlistReference(),
            waitlistEntry.getTourId(),
            tourTitle,
            waitlistEntry.getCustomerName(),
            waitlistEntry.getEmail(),
            waitlistEntry.getGuests(),
            waitlistEntry.getTravelDate(),
            waitlistEntry.getStatus().name().toLowerCase(),
            priority,
            waitlistEntry.getBookingId(),
            waitlistEntry.getCreatedAt()
        );
    }
}
