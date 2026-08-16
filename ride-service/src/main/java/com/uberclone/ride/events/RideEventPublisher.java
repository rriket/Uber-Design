package com.uberclone.ride.events;

import com.uberclone.common.events.RideEvents.*;
import com.uberclone.common.events.Topics;
import com.uberclone.ride.domain.Ride;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.time.Instant;

/**
 * Publishes domain events onto Kafka. Downstream services (matching,
 * payments, notifications, analytics, …) subscribe independently.
 * Falls back to a warning log if Kafka is unreachable so a broken bus
 * never crashes the ride-request user flow.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RideEventPublisher {

    private final KafkaTemplate<String, Object> kafka;

    public void publishRequested(Ride r) {
        publish(Topics.RIDE_REQUESTED, r.getId().toString(),
                new RideRequested(
                        r.getId().toString(), r.getRiderId(),
                        r.getPickupLat(), r.getPickupLng(),
                        r.getDestLat(), r.getDestLng(),
                        r.getCategory().name(), r.getQuotedPrice(),
                        Instant.now()));
    }

    public void publishScheduled(Ride r) {
        publish(Topics.RIDE_SCHEDULED, r.getId().toString(),
                new RideScheduled(r.getId().toString(), r.getRiderId(),
                        r.getScheduledFor(), Instant.now()));
    }

    public void publishAccepted(Ride r) {
        publish(Topics.RIDE_ACCEPTED, r.getId().toString(),
                new RideAccepted(r.getId().toString(), r.getRiderId(), r.getDriverId(), Instant.now()));
    }

    public void publishCompleted(Ride r) {
        publish(Topics.RIDE_COMPLETED, r.getId().toString(),
                new RideCompleted(r.getId().toString(), r.getRiderId(), r.getDriverId(),
                        r.getQuotedPrice(), "usd", Instant.now()));
    }

    public void publishCancelled(Ride r, String cancelledBy) {
        publish(Topics.RIDE_CANCELLED, r.getId().toString(),
                new RideCancelled(r.getId().toString(), cancelledBy, Instant.now()));
    }

    private void publish(String topic, String key, Object payload) {
        try {
            kafka.send(topic, key, payload);
            log.info("→ Kafka {} key={} payload={}", topic, key, payload);
        } catch (Exception e) {
            log.warn("Kafka publish to {} failed: {}. Downstream may be delayed.", topic, e.getMessage());
        }
    }
}
