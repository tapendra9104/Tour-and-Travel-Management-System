package com.toursim.management.payment;

public enum PaymentMethod {
    CARD("Card"),
    CREDIT_CARD("Credit Card"),
    DEBIT_CARD("Debit Card"),
    UPI("UPI"),
    NET_BANKING("Net Banking"),
    BANK_TRANSFER("Bank Transfer"),
    PAYPAL("PayPal"),
    GOOGLE_PAY("Google Pay"),
    APPLE_PAY("Apple Pay"),
    PHONEPE("PhonePe"),
    PAYTM("Paytm"),
    AMAZON_PAY("Amazon Pay"),
    VENMO("Venmo"),
    CASH_APP("Cash App"),
    ZELLE("Zelle"),
    WALLET("Wallet"),
    EMI("EMI / Installments"),
    BNPL("Buy Now Pay Later"),
    CASH("Cash"),
    CHEQUE("Cheque");

    private final String label;

    PaymentMethod(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
