package com.uberclone.ratings.dto;

public record UserRatingSummary(
        String userId,
        String role,
        double averageStars,
        long count
) {}
