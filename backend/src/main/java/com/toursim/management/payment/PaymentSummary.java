package com.toursim.management.payment;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public record PaymentSummary(
    String currency,
    PaymentStatus status,
    PaymentStage nextStage,
    BigDecimal totalAmount,
    BigDecimal paidAmount,
    BigDecimal refundedAmount,
    BigDecimal netPaidAmount,
    BigDecimal outstandingAmount,
    BigDecimal depositAmount,
    BigDecimal depositOutstandingAmount,
    BigDecimal balanceOutstandingAmount,
    BigDecimal dueNowAmount,
    BigDecimal refundableAmount,
    BigDecimal cancellationFeeAmount,
    LocalDate dueDate,
    boolean overdue,
    PaymentMethod lastMethod,
    String lastReceiptNumber,
    String lastTransactionReference,
    LocalDateTime lastPaidAt
) {
}
