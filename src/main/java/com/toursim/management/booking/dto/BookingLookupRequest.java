package com.toursim.management.booking.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record BookingLookupRequest(
    @NotBlank String bookingReference,
    @NotBlank @Email String email
) {
}
