package com.uberclone.ride.dto;

import com.uberclone.common.dto.RideStatus;
import com.uberclone.common.dto.RideCategory;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record RideResponse(
        UUID rideId,
        String riderId,
        String driverId,
        UUID fareId,
        RideCategory category,
        Double pickupLat, Double pickupLng, String pickupAddress,
        Double destLat, Double destLng, String destAddress,
        BigDecimal quotedPrice,
        BigDecimal finalPrice,
        RideStatus status,
        Instant createdAt,
        Instant acceptedAt,
        Instant pickedUpAt,
        Instant completedAt,
        Instant scheduledFor
) {}
