package com.uberclone.common.dto;

import jakarta.validation.constraints.NotNull;

public record LatLng(
        @NotNull Double lat,
        @NotNull Double lng
) {
    public static double distanceKm(LatLng a, LatLng b) {
        double R = 6371.0;
        double dLat = Math.toRadians(b.lat - a.lat);
        double dLng = Math.toRadians(b.lng - a.lng);
        double h = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(a.lat)) * Math.cos(Math.toRadians(b.lat))
                * Math.sin(dLng / 2) * Math.sin(dLng / 2);
        return 2 * R * Math.asin(Math.sqrt(h));
    }
}
