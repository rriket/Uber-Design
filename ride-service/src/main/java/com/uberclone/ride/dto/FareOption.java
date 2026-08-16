package com.uberclone.ride.dto;

import com.uberclone.common.dto.RideCategory;

import java.math.BigDecimal;
import java.util.UUID;

public record FareOption(
        UUID fareId,
        RideCategory category,
        String label,        // Friendly name shown in UI: "UberX", "UberXL", "Comfort"
        String description,  // Sub-line shown under label
        Integer capacity,    // Passenger capacity
        BigDecimal price,
        String currency,
        Integer etaMinutes,
        Double distanceKm
) {}
