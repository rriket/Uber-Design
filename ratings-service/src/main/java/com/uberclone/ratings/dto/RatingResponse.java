package com.uberclone.ratings.dto;

import java.time.Instant;
import java.util.UUID;

public record RatingResponse(
        UUID id,
        UUID rideId,
        String ratedByUserId,
        String ratedUserId,
        String ratedRole,
        int stars,
        String comment,
        Instant createdAt
) {}
