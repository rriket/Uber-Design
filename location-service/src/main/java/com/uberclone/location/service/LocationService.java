package com.uberclone.location.service;

import com.uberclone.common.dto.LatLng;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.geo.Distance;
import org.springframework.data.geo.Metrics;
import org.springframework.data.geo.Point;
import org.springframework.data.redis.connection.RedisGeoCommands;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.domain.geo.GeoReference;
import org.springframework.data.redis.domain.geo.GeoShape;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Manages driver locations in Redis using GEOADD / GEOSEARCH.
 *
 * Storage layout:
 *  - Geo set:   drivers:geo   (member = driverId, score = geohash)
 *  - ZSET:      drivers:heartbeat  (member = driverId, score = epoch millis of last update)
 *  - String:    drivers:online:{driverId} = "ONLINE" (with TTL for automatic cleanup)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LocationService {

    public static final String GEO_KEY = "drivers:geo";
    public static final String HEARTBEAT_KEY = "drivers:heartbeat";
    public static final String ONLINE_KEY_PREFIX = "drivers:online:";

    private final StringRedisTemplate redis;

    @Value("${uber.location.online-ttl-seconds:60}")
    private long onlineTtlSeconds;

    @Value("${uber.location.stale-threshold-seconds:60}")
    private long staleThresholdSeconds;

    public void updateLocation(String driverId, LatLng loc) {
        redis.opsForGeo().add(GEO_KEY, new Point(loc.lng(), loc.lat()), driverId);
        redis.opsForZSet().add(HEARTBEAT_KEY, driverId, Instant.now().toEpochMilli());
        redis.opsForValue().set(ONLINE_KEY_PREFIX + driverId, "ONLINE",
                java.time.Duration.ofSeconds(onlineTtlSeconds));
    }

    public void goOffline(String driverId) {
        redis.opsForGeo().remove(GEO_KEY, driverId);
        redis.opsForZSet().remove(HEARTBEAT_KEY, driverId);
        redis.delete(ONLINE_KEY_PREFIX + driverId);
    }

    public boolean isOnline(String driverId) {
        return Boolean.TRUE.equals(redis.hasKey(ONLINE_KEY_PREFIX + driverId));
    }

    public LatLng getLocation(String driverId) {
        var positions = redis.opsForGeo().position(GEO_KEY, driverId);
        if (positions == null || positions.isEmpty() || positions.get(0) == null) return null;
        Point p = positions.get(0);
        return new LatLng(p.getY(), p.getX());
    }

    public List<NearbyDriver> findNearby(LatLng center, double radiusKm, int limit) {
        var results = redis.opsForGeo().search(
                GEO_KEY,
                GeoReference.fromCoordinate(new Point(center.lng(), center.lat())),
                GeoShape.byRadius(new Distance(radiusKm, Metrics.KILOMETERS)),
                RedisGeoCommands.GeoSearchCommandArgs.newGeoSearchArgs()
                        .includeDistance().includeCoordinates().sortAscending().limit(limit)
        );
        List<NearbyDriver> out = new ArrayList<>();
        if (results == null) return out;
        results.getContent().forEach(r -> {
            var member = r.getContent().getName();
            var pt = r.getContent().getPoint();
            double distKm = r.getDistance().getValue();
            out.add(new NearbyDriver(member, pt.getY(), pt.getX(), distKm));
        });
        return out;
    }

    /** Periodic cleanup of stale drivers (>staleThresholdSeconds since last heartbeat). */
    @Scheduled(fixedDelayString = "${uber.location.cleanup-interval-ms:30000}")
    public void cleanupStale() {
        long cutoff = Instant.now().toEpochMilli() - (staleThresholdSeconds * 1000L);
        Set<String> stale = redis.opsForZSet().rangeByScore(HEARTBEAT_KEY, 0, cutoff);
        if (stale == null || stale.isEmpty()) return;
        stale.forEach(this::goOffline);
        log.info("Cleaned up {} stale drivers", stale.size());
    }

    public record NearbyDriver(String driverId, double lat, double lng, double distanceKm) {}
}
