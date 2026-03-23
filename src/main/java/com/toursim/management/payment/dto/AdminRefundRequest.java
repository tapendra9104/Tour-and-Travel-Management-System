package com.toursim.management.payment.dto;

import java.math.BigDecimal;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Size;

public record AdminRefundRequest(
    @DecimalMin("0.01") BigDecimal amount,
    @Size(max = 500) String note
) {
}
