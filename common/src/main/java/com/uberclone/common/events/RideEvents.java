package com.uberclone.common.events;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Immutable payloads flowing through Kafka. All events carry a `rideId` for
 * correlation and an `occurredAt` for eventual sourcing / audit.
 */
public final class RideEvents {
    private RideEvents() {}

    public record RideRequested(
            String rideId,
            String riderId,
            double pickupLat, double pickupLng,
            double destLat, double destLng,
            String category,
            BigDecimal quotedPrice,
            Instant occurredAt
    ) {}

    public record RideScheduled(
            String rideId,
            String riderId,
            Instant scheduledFor,
            Instant occurredAt
    ) {}

    public record RideAccepted(
            String rideId,
            String riderId,
            String driverId,
            Instant occurredAt
    ) {}

    public record RideCompleted(
            String rideId,
            String riderId,
            String driverId,
            BigDecimal amount,
            String currency,
            Instant occurredAt
    ) {}

    public record RideCancelled(
            String rideId,
            String cancelledBy,
            Instant occurredAt
    ) {}

    public record PaymentCharged(
            String rideId,
            String riderId,
            String driverId,
            BigDecimal amount,
            BigDecimal driverEarnings,
            BigDecimal platformFee,
            String status,          // SUCCEEDED / FAILED / MOCKED
            String currency,
            Instant occurredAt
    ) {}
}
