package com.toursim.management.payment;

public interface PaymentGateway {

    PaymentGatewayResult charge(PaymentGatewayChargeRequest request);

    PaymentGatewayResult refund(PaymentGatewayRefundRequest request);
}
