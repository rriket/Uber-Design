package com.uberclone.ratings.dto;

import jakarta.validation.constraints.*;

import java.util.UUID;

public record SubmitRatingRequest(
        @NotNull UUID rideId,
        @NotBlank String ratedUserId,       // driverId or riderId
        @NotBlank @Pattern(regexp = "DRIVER|RIDER") String ratedRole,
        @Min(1) @Max(5) int stars,
        @Size(max = 500) String comment
) {}
