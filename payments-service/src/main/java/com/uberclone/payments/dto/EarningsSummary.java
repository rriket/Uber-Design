package com.uberclone.payments.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record EarningsSummary(
        String driverId,
        int days,
        BigDecimal totalEarnings,
        int tripCount,
        List<DailyBucket> daily
) {
    public record DailyBucket(LocalDate date, BigDecimal earnings, int tripCount) {}
}
