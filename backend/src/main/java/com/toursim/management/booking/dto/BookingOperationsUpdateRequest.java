package com.toursim.management.booking.dto;

import jakarta.validation.constraints.Size;

public record BookingOperationsUpdateRequest(
    @Size(max = 40) String transportMode,
    @Size(max = 80) String transportClass,
    @Size(max = 40) String transportStatus,
    Boolean documentsVerified,
    @Size(max = 40) String operationsPriority,
    @Size(max = 500) String operationsNotes
) {
}
