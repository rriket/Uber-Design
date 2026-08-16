package com.uberclone.ride.dto;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record RequestRideRequest(@NotNull UUID fareId) {}
