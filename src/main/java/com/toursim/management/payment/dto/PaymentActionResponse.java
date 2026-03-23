package com.toursim.management.payment.dto;

import com.toursim.management.payment.PaymentActionResult;

public record PaymentActionResponse(
    String message,
    String transactionReference,
    String receiptNumber
) {

    public static PaymentActionResponse from(PaymentActionResult result) {
        return new PaymentActionResponse(
            result.message(),
            result.transaction().getTransactionReference(),
            result.transaction().getReceiptNumber()
        );
    }
}
