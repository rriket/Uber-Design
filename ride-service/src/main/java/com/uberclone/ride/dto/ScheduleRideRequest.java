package com.uberclone.ride.dto;

import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.UUID;

public record ScheduleRideRequest(
        @NotNull UUID fareId,
        @NotNull Instant scheduledFor
) {}
