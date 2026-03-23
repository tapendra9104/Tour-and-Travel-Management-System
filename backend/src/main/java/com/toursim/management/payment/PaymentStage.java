package com.toursim.management.payment;

public enum PaymentStage {
    DEPOSIT("Deposit"),
    BALANCE("Balance"),
    FULL("Full Payment"),
    REFUND("Refund");

    private final String label;

    PaymentStage(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
