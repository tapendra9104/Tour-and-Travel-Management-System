package com.toursim.management.inquiry;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record InquiryRequest(
    @NotBlank(message = "Full name is required")
    @Size(max = 120, message = "Full name must be 120 characters or fewer")
    String customerName,
    @NotBlank(message = "Email address is required")
    @Email(message = "Enter a valid email address")
    String email,
    @NotBlank(message = "Phone number is required")
    @Size(max = 30, message = "Phone number must be 30 characters or fewer")
    String phone,
    @Size(max = 120, message = "Destination of interest must be 120 characters or fewer")
    String destination,
    @Size(max = 80, message = "Travel window must be 80 characters or fewer")
    String travelWindow,
    @Min(value = 1, message = "At least one traveler is required")
    @Max(value = 20, message = "Traveler count cannot exceed 20")
    int travelers,
    @NotBlank(message = "Tell us how we can help")
    @Size(max = 2000, message = "Inquiry message must be 2000 characters or fewer")
    String message
) {
}
