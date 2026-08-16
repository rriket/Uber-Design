package com.uberclone.ride.surge;

import com.uberclone.common.dto.LatLng;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.UUID;

/**
 * Simple sliding-window demand tracker per geo-cell.
 *
 * Cell key = 3-decimal-place lat/lng bucket (~110m at equator — small enough to detect
 * hotspots without being too noisy). Every fare estimate ZADDs the current epoch
 * millis into "surge:cell:{lat}:{lng}". We ZCOUNT the last 60 seconds to compute
 * request density. If density crosses the threshold, a multiplier applies.
 *
 * Multiplier bands:
 *   < 10 req/min  → 1.0x  (no surge)
 *   10-19         → 1.3x
 *   20-39         → 1.7x
 *   >= 40         → 2.5x
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SurgePricingService {

    private final StringRedisTemplate redis;

    @Value("${uber.surge.window-seconds:60}") private long windowSeconds;
    @Value("${uber.surge.enabled:true}")      private boolean enabled;

    public void recordRequest(LatLng pickup) {
        String key = cellKey(pickup);
        long now = Instant.now().toEpochMilli();
        redis.opsForZSet().add(key, UUID.randomUUID() + ":" + now, now);
        redis.expire(key, java.time.Duration.ofSeconds(windowSeconds * 3L)); // keep some history
    }

    public SurgeInfo compute(LatLng pickup) {
        if (!enabled) return new SurgeInfo(1.0, 0, "NORMAL");

        String key = cellKey(pickup);
        long now = Instant.now().toEpochMilli();
        long cutoff = now - windowSeconds * 1000L;

        // Trim old entries
        redis.opsForZSet().removeRangeByScore(key, 0, cutoff);
        Long count = redis.opsForZSet().count(key, cutoff, now);
        long c = count == null ? 0 : count;

        double mult; String band;
        if (c < 10)      { mult = 1.0; band = "NORMAL"; }
        else if (c < 20) { mult = 1.3; band = "MODERATE"; }
        else if (c < 40) { mult = 1.7; band = "HIGH"; }
        else             { mult = 2.5; band = "EXTREME"; }

        return new SurgeInfo(mult, (int) c, band);
    }

    private String cellKey(LatLng p) {
        return String.format("surge:cell:%.3f:%.3f", p.lat(), p.lng());
    }

    public record SurgeInfo(double multiplier, int recentRequestCount, String band) {}
}
