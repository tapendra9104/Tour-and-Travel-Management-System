package com.toursim.management.booking.dto;

import java.time.LocalDate;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record BookingSelfServiceRequest(
    @NotBlank String bookingReference,
    @NotBlank @Email String email,
    LocalDate date,
    @Size(max = 500) String note
) {
}
