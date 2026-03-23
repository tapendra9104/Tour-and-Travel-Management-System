package com.toursim.management.payment;

public record PaymentGatewayResult(
    String providerName,
    String providerReference,
    String receiptNumber
) {
}
