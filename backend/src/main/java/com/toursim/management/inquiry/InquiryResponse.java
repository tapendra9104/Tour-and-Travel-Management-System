package com.toursim.management.inquiry;

import java.time.LocalDateTime;

import com.fasterxml.jackson.annotation.JsonProperty;

public record InquiryResponse(
    Long id,
    String customerName,
    String email,
    String phone,
    String destination,
    String travelWindow,
    int travelers,
    String message,
    @JsonProperty("status") String statusValue,
    String adminNotes,
    String priority,
    long ageHours,
    LocalDateTime createdAt
) {

    public static InquiryResponse from(Inquiry inquiry) {
        return from(inquiry, "normal", 0);
    }

    public static InquiryResponse from(Inquiry inquiry, String priority, long ageHours) {
        return new InquiryResponse(
            inquiry.getId(),
            inquiry.getCustomerName(),
            inquiry.getEmail(),
            inquiry.getPhone(),
            inquiry.getDestination(),
            inquiry.getTravelWindow(),
            inquiry.getTravelers(),
            inquiry.getMessage(),
            inquiry.getStatus().name().toLowerCase(),
            inquiry.getAdminNotes(),
            priority,
            ageHours,
            inquiry.getCreatedAt()
        );
    }
}
