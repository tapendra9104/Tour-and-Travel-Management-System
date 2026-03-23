package com.toursim.management.booking.dto;

public record BookingSubmissionResponse(
    String outcome,
    String message,
    BookingResponse booking,
    String waitlistReference
) {
}
