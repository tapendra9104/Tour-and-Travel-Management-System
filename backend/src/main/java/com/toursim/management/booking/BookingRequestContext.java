package com.toursim.management.booking;

import java.time.LocalDate;

public record BookingRequestContext(
    String customerName,
    String email,
    String phone,
    int guests,
    LocalDate date
) {
}
