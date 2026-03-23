package com.toursim.management.booking.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record BookingPreferenceUpdateRequest(
    @NotBlank String bookingReference,
    @NotBlank @Email String email,
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
