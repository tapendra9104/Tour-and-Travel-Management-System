package com.toursim.management.booking;

import com.toursim.management.waitlist.WaitlistEntry;

public record BookingSubmissionResult(
    String outcome,
    String message,
    Booking booking,
    WaitlistEntry waitlistEntry
) {
}
