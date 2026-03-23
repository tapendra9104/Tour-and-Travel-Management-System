package com.toursim.management.payment.dto;

import java.math.BigDecimal;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record AdminPaymentRequest(
    @NotBlank String method,
    @NotNull @DecimalMin("0.01") BigDecimal amount,
    @Size(max = 500) String note
) {
}
