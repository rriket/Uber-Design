package com.uberclone.payments.dto;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.util.UUID;

public record ChargeRideRequest(
        @NotNull UUID rideId,
        @NotBlank String riderId,
        @NotBlank String driverId,
        @NotNull @DecimalMin("0.01") BigDecimal amount,
        @NotBlank String currency
) {}
