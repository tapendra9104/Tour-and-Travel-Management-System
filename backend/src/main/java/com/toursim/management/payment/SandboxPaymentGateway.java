package com.toursim.management.payment;

import java.time.LocalDate;
import java.util.Locale;
import java.util.UUID;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Simulated payment gateway used in development and demo environments.
 * Active by default (when {@code app.payments.gateway} is unset or set to {@code sandbox}).
 * Switch to a real gateway by setting {@code PAYMENT_GATEWAY=stripe} (see StripePaymentGateway).
 */
@Component
@ConditionalOnProperty(name = "app.payments.gateway", havingValue = "sandbox", matchIfMissing = true)
public class SandboxPaymentGateway implements PaymentGateway {

    private static final String PROVIDER = "Wanderlust Sandbox Pay";

    @Override
    public PaymentGatewayResult charge(PaymentGatewayChargeRequest request) {
        return new PaymentGatewayResult(
            PROVIDER,
            "CHG-" + token(),
            receiptPrefix(request.method()) + "-" + LocalDate.now().format(java.time.format.DateTimeFormatter.BASIC_ISO_DATE) + "-" + token()
        );
    }

    @Override
    public PaymentGatewayResult refund(PaymentGatewayRefundRequest request) {
        return new PaymentGatewayResult(
            PROVIDER,
            "RFD-" + token(),
            "RFD-" + LocalDate.now().format(java.time.format.DateTimeFormatter.BASIC_ISO_DATE) + "-" + token()
        );
    }

    private String receiptPrefix(PaymentMethod method) {
        return switch (method) {
            case CARD -> "CARD";
            case CREDIT_CARD -> "CC";
            case DEBIT_CARD -> "DC";
            case UPI -> "UPI";
            case NET_BANKING -> "NET";
            case BANK_TRANSFER -> "BANK";
            case PAYPAL -> "PYPL";
            case GOOGLE_PAY -> "GPAY";
            case APPLE_PAY -> "APAY";
            case PHONEPE -> "PHPE";
            case PAYTM -> "PAYTM";
            case AMAZON_PAY -> "AMZN";
            case VENMO -> "VENMO";
            case CASH_APP -> "CAPP";
            case ZELLE -> "ZELLE";
            case WALLET -> "WALLET";
            case EMI -> "EMI";
            case BNPL -> "BNPL";
            case CASH -> "CASH";
            case CHEQUE -> "CHQ";
        };
    }

    private String token() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 10).toUpperCase(Locale.ROOT);
    }
}
