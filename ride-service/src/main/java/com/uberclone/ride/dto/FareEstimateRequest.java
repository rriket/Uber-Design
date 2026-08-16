package com.uberclone.ride.dto;

import com.uberclone.common.dto.LatLng;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record FareEstimateRequest(
        @NotNull @Valid LatLng pickup,
        @NotBlank String pickupAddress,
        @NotNull @Valid LatLng destination,
        @NotBlank String destinationAddress
) {}
