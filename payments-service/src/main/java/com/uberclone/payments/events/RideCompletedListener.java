package com.uberclone.payments.events;

import com.uberclone.common.events.RideEvents.PaymentCharged;
import com.uberclone.common.events.RideEvents.RideCompleted;
import com.uberclone.common.events.Topics;
import com.uberclone.payments.dto.ChargeRideRequest;
import com.uberclone.payments.dto.PaymentResponse;
import com.uberclone.payments.service.PaymentsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.UUID;

/**
 * Payments-service consumes `ride.completed` events and triggers a Stripe charge.
 * When done it publishes a `payment.charged` event so downstream consumers
 * (e.g. notifications, analytics) can react without payments knowing about them.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RideCompletedListener {

    private final PaymentsService paymentsService;
    private final KafkaTemplate<String, Object> kafka;

    @KafkaListener(topics = Topics.RIDE_COMPLETED, groupId = "payments-service")
    public void onRideCompleted(RideCompleted evt) {
        log.info("← Kafka {} rideId={} amount={} {}", Topics.RIDE_COMPLETED, evt.rideId(), evt.amount(), evt.currency());
        PaymentResponse resp = paymentsService.chargeRide(new ChargeRideRequest(
                UUID.fromString(evt.rideId()),
                evt.riderId(), evt.driverId(),
                evt.amount(), evt.currency()
        ));
        PaymentCharged out = new PaymentCharged(
                resp.rideId().toString(), resp.riderId(), resp.driverId(),
                resp.amount(), resp.driverEarnings(), resp.platformFee(),
                resp.status(), resp.currency(), Instant.now());
        kafka.send(Topics.PAYMENT_CHARGED, evt.rideId(), out);
        log.info("→ Kafka {} rideId={} status={}", Topics.PAYMENT_CHARGED, evt.rideId(), resp.status());
    }
}
