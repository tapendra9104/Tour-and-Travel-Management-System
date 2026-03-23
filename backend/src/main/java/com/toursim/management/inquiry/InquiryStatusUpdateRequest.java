package com.toursim.management.inquiry;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record InquiryStatusUpdateRequest(
    @NotNull InquiryStatus status,
    @Size(max = 1000) String adminNotes
) {
}
