package com.toursim.management.booking.dto;

import com.toursim.management.booking.BookingStatus;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record BookingStatusUpdateRequest(
    @NotNull BookingStatus status,
    @Size(max = 500) String note
) {
}
