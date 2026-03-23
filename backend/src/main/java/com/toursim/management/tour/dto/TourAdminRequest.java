package com.toursim.management.tour.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record TourAdminRequest(
    String id,
    @NotBlank @Size(max = 255) String title,
    @NotBlank @Size(max = 255) String destination,
    @NotBlank @Size(max = 255) String country,
    @NotBlank @Size(max = 255) String duration,
    @NotNull @DecimalMin("0.0") BigDecimal price,
    BigDecimal originalPrice,
    @DecimalMin("0.0") BigDecimal rating,
    @Min(0) int reviews,
    @NotBlank @Size(max = 1000) String image,
    @NotBlank @Size(max = 255) String category,
    @NotBlank @Size(max = 4000) String description,
    @NotBlank @Size(max = 255) String difficulty,
    @Min(1) int maxGroupSize,
    @NotEmpty List<@NotBlank String> highlights,
    @NotEmpty List<@NotBlank String> included,
    @NotEmpty List<@NotNull LocalDate> startDates
) {
}
