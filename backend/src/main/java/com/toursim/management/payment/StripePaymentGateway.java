package com.toursim.management.payment;

import java.time.LocalDate;
import java.util.Locale;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Stripe-backed PaymentGateway implementation.
 *
 * <p>Activated when {@code app.payments.gateway=stripe} is set in your environment.
 *
 * <h3>Setup</h3>
 * <ol>
 *   <li>Add the Stripe Java SDK to pom.xml:
 *   <pre>
 *   &lt;dependency&gt;
 *     &lt;groupId&gt;com.stripe&lt;/groupId&gt;
 *     &lt;artifactId&gt;stripe-java&lt;/artifactId&gt;
 *     &lt;version&gt;25.9.0&lt;/version&gt;
 *   &lt;/dependency&gt;
 *   </pre>
 *   <li>Set environment variables:
 *   <pre>
 *   PAYMENT_GATEWAY=stripe
 *   STRIPE_SECRET_KEY=sk_live_...      # or sk_test_... for testing
 *   STRIPE_WEBHOOK_SECRET=whsec_...    # optional, for webhook signature verification
 *   </pre>
 *   <li>Uncomment and implement the Stripe API calls below in {@link #charge} and {@link #refund}.
 * </ol>
 *
 * <h3>Webhook (optional)</h3>
 * To receive async payment confirmation events from Stripe, create a webhook endpoint at
 * {@code POST /api/stripe/webhook} and verify the signature using {@code STRIPE_WEBHOOK_SECRET}.
 */
@Component
@ConditionalOnProperty(name = "app.payments.gateway", havingValue = "stripe")
public class StripePaymentGateway implements PaymentGateway {

    private static final Logger log = LoggerFactory.getLogger(StripePaymentGateway.class);
    private static final String PROVIDER = "Stripe";

    // @Value("${STRIPE_SECRET_KEY}")
    // private String secretKey;

    /**
     * Charges the customer via Stripe PaymentIntent.
     *
     * <p>Implementation guide:
     * <pre>
     * Stripe.apiKey = secretKey;
     * PaymentIntentCreateParams params = PaymentIntentCreateParams.builder()
     *     .setAmount(request.amount().multiply(BigDecimal.valueOf(100)).longValue()) // cents
     *     .setCurrency(request.currency().toLowerCase())
     *     .setDescription("Booking " + request.bookingReference())
     *     .setReceiptEmail(request.customerEmail())
     *     .build();
     * PaymentIntent intent = PaymentIntent.create(params);
     * return new PaymentGatewayResult(PROVIDER, intent.getId(), intent.getId());
     * </pre>
     */
    @Override
    public PaymentGatewayResult charge(PaymentGatewayChargeRequest request) {
        log.warn("StripePaymentGateway.charge() is a stub — implement Stripe SDK calls. " +
                 "Returning a simulated result for booking: {}", request.bookingReference());
        // TODO: Replace with real Stripe PaymentIntent.create() call
        return new PaymentGatewayResult(PROVIDER, "pi_stub_" + token(), "rcpt_stripe_" + token());
    }

    /**
     * Issues a refund via Stripe Refund API.
     *
     * <p>Implementation guide:
     * <pre>
     * Stripe.apiKey = secretKey;
     * RefundCreateParams params = RefundCreateParams.builder()
     *     .setPaymentIntent(request.providerReference())
     *     .setAmount(request.amount().multiply(BigDecimal.valueOf(100)).longValue())
     *     .build();
     * Refund refund = Refund.create(params);
     * return new PaymentGatewayResult(PROVIDER, refund.getId(), refund.getId());
     * </pre>
     */
    @Override
    public PaymentGatewayResult refund(PaymentGatewayRefundRequest request) {
        log.warn("StripePaymentGateway.refund() is a stub — implement Stripe SDK calls. " +
                 "Returning a simulated result.");
        // TODO: Replace with real Stripe Refund.create() call
        return new PaymentGatewayResult(PROVIDER, "re_stub_" + token(),
            "RFD-STRIPE-" + LocalDate.now().format(java.time.format.DateTimeFormatter.BASIC_ISO_DATE) + "-" + token());
    }

    private String token() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 12).toUpperCase(Locale.ROOT);
    }
}
