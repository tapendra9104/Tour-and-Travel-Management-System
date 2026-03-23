package com.toursim.management.payment;

import java.math.BigDecimal;

public record PaymentGatewayRefundRequest(
    String bookingReference,
    String customerName,
    BigDecimal amount,
    String currency,
    PaymentMethod method,
    String originalTransactionReference
) {
}
