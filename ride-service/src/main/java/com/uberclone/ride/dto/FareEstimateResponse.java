package com.uberclone.ride.dto;

import java.util.List;

public record FareEstimateResponse(
        String pickupAddress,
        String destinationAddress,
        Double distanceKm,
        Integer baseEtaMinutes,
        Boolean mockedMapping,   // true if we used the haversine fallback
        Double surgeMultiplier,  // 1.0 = no surge
        String surgeBand,        // NORMAL / MODERATE / HIGH / EXTREME
        List<FareOption> options
) {}
