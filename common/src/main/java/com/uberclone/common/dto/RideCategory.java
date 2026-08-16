package com.uberclone.common.dto;

/**
 * Ride tier. Prices are computed as a multiplier over the base per-km/per-min pricing.
 * ETAs adjust slightly by category (XL vehicles are slower to spot, Comfort has premium drivers).
 */
public enum RideCategory {
    UBER_X(1.00,  0),   // Standard
    UBER_XL(1.75, 1),   // 6+ seats
    UBER_COMFORT(1.40, 0); // Newer cars, top-rated drivers

    private final double priceMultiplier;
    private final int etaExtraMinutes;

    RideCategory(double priceMultiplier, int etaExtraMinutes) {
        this.priceMultiplier = priceMultiplier;
        this.etaExtraMinutes = etaExtraMinutes;
    }

    public double priceMultiplier() { return priceMultiplier; }
    public int etaExtraMinutes()    { return etaExtraMinutes; }
}
