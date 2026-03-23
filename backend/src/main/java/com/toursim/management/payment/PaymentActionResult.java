package com.toursim.management.payment;

public record PaymentActionResult(
    PaymentTransaction transaction,
    PaymentSummary summary,
    String message
) {
}
