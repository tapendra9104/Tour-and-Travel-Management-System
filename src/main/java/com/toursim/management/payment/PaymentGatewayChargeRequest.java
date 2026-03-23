package com.toursim.management.payment;

import java.math.BigDecimal;

public record PaymentGatewayChargeRequest(
    String bookingReference,
    String customerName,
    String customerEmail,
    BigDecimal amount,
    String currency,
    PaymentMethod method,
    PaymentStage stage
) {
}
