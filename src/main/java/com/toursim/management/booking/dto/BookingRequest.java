package com.toursim.management.booking.dto;

import java.time.LocalDate;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record BookingRequest(
    @NotBlank String tourId,
    @NotBlank String customerName,
    @NotBlank @Email String email,
    @NotBlank String phone,
    @NotNull @Min(1) @Max(20) Integer guests,
    @NotNull LocalDate date,
    @Size(max = 80) String mealPreference,
    @Size(max = 500) String dietaryRestrictions,
    @Size(max = 80) String occasionType,
    @Size(max = 500) String occasionNotes,
    @Size(max = 120) String roomPreference,
    @Size(max = 80) String tripStyle,
    @Size(max = 500) String assistanceNotes,
    Boolean transferRequired,
    @Size(max = 500) String travelerNotes,
    @Size(max = 40) String transportMode,
    @Size(max = 80) String transportClass
) {
}
