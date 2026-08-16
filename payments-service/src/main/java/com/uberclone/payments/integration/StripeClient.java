package com.uberclone.payments.integration;

import com.stripe.Stripe;
import com.stripe.exception.StripeException;
import com.stripe.model.PaymentIntent;
import com.stripe.param.PaymentIntentCreateParams;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Map;

/**
 * Wraps the Stripe Java SDK. In test mode we auto-confirm PaymentIntents
 * using the `pm_card_visa` test payment method so end-to-end demos work
 * without collecting real card details.
 *
 * If STRIPE_API_KEY is missing or Stripe is unreachable, {@link #createCharge}
 * returns a MockedResult so the ride flow still completes locally.
 */
@Slf4j
@Component
public class StripeClient {

    @Value("${stripe.api-key:}")            private String apiKey;
    @Value("${stripe.test-payment-method:pm_card_visa}") private String testPaymentMethod;
    @Value("${stripe.enabled:true}")        private boolean enabled;

    @PostConstruct
    void init() {
        if (apiKey != null && !apiKey.isBlank()) {
            Stripe.apiKey = apiKey;
            log.info("Stripe SDK initialised in {} mode", apiKey.startsWith("sk_live") ? "LIVE" : "TEST");
        } else {
            log.warn("STRIPE_API_KEY not set — payments will be MOCKED (no real Stripe calls).");
        }
    }

    public ChargeResult createCharge(String rideId, BigDecimal amount, String currency,
                                     String riderId, String driverId) {
        if (!enabled || apiKey == null || apiKey.isBlank()) {
            return new ChargeResult(true, null, "MOCKED", null);
        }
        try {
            long unitAmount = amount.setScale(2, RoundingMode.HALF_UP)
                    .movePointRight(2).longValueExact(); // dollars → cents

            PaymentIntentCreateParams params = PaymentIntentCreateParams.builder()
                    .setAmount(unitAmount)
                    .setCurrency(currency.toLowerCase())
                    .setPaymentMethod(testPaymentMethod)
                    .setConfirm(true)
                    .setOffSession(true)
                    // In test mode disable redirect-based auth so pm_card_visa succeeds automatically.
                    .setAutomaticPaymentMethods(
                            PaymentIntentCreateParams.AutomaticPaymentMethods.builder()
                                    .setEnabled(true)
                                    .setAllowRedirects(PaymentIntentCreateParams.AutomaticPaymentMethods.AllowRedirects.NEVER)
                                    .build())
                    .putAllMetadata(Map.of(
                            "ride_id", rideId,
                            "rider_id", riderId,
                            "driver_id", driverId
                    ))
                    .build();

            PaymentIntent intent = PaymentIntent.create(params);
            log.info("Stripe PaymentIntent {} status={} amount={} {}", intent.getId(), intent.getStatus(), amount, currency);

            boolean succeeded = "succeeded".equals(intent.getStatus());
            return new ChargeResult(succeeded, intent.getId(),
                    succeeded ? "SUCCEEDED" : "FAILED",
                    succeeded ? null : "Stripe status: " + intent.getStatus());
        } catch (StripeException e) {
            log.error("Stripe charge failed for ride {}: {}", rideId, e.getMessage());
            return new ChargeResult(false, null, "FAILED", e.getMessage());
        }
    }

    public record ChargeResult(boolean success, String paymentIntentId, String status, String failureMessage) {}
}
