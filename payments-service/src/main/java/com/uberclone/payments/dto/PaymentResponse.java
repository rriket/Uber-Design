package com.uberclone.payments.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record PaymentResponse(
        UUID id, UUID rideId, String riderId, String driverId,
        BigDecimal amount, BigDecimal driverEarnings, BigDecimal platformFee,
        String currency, String status,
        String stripePaymentIntentId,
        String failureMessage,
        Instant createdAt, Instant updatedAt
) {}
