package com.toursim.management.payment;

public enum PaymentStatus {
    UNPAID("Unpaid"),
    DEPOSIT_DUE("Deposit Due"),
    BALANCE_DUE("Balance Due"),
    PARTIALLY_PAID("Partially Paid"),
    PAID_IN_FULL("Paid in Full"),
    OVERDUE("Overdue"),
    REFUND_DUE("Refund Due"),
    PARTIALLY_REFUNDED("Partially Refunded"),
    REFUNDED("Refunded"),
    CANCELLED("Cancelled");

    private final String label;

    PaymentStatus(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
