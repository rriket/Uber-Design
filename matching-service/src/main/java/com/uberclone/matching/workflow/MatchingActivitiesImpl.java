package com.uberclone.matching.workflow;

import com.fasterxml.jackson.databind.JsonNode;
import com.uberclone.matching.lock.DistributedLock;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class MatchingActivitiesImpl implements MatchingActivities {

    private final DistributedLock lock;
    private final RestClient restClient = RestClient.create();

    @Value("${uber.services.ride-url:http://localhost:8081}")        private String rideUrl;
    @Value("${uber.services.location-url:http://localhost:8082}")    private String locationUrl;
    @Value("${uber.services.notification-url:http://localhost:8084}") private String notificationUrl;

    @Value("${uber.matching.max-candidates:10}") private int maxCandidates;
    @Value("${uber.matching.search-radius-km:5}") private double searchRadiusKm;

    @Override
    public RideSnapshot fetchRide(String rideId) {
        try {
            JsonNode j = restClient.get().uri(rideUrl + "/api/rides/" + rideId).retrieve().body(JsonNode.class);
            if (j == null) return null;
            return new RideSnapshot(rideId,
                    j.path("status").asText(),
                    j.path("driverId").asText(null),
                    j.path("pickupLat").asDouble(),
                    j.path("pickupLng").asDouble());
        } catch (Exception e) {
            log.warn("fetchRide failed: {}", e.getMessage());
            return null;
        }
    }

    @Override
    public RideSnapshot pollRide(String rideId) {
        return fetchRide(rideId);
    }

    @SuppressWarnings("unchecked")
    @Override
    public List<CandidateDriver> fetchNearbyDrivers(double lat, double lng) {
        try {
            List<?> raw = restClient.get()
                    .uri(locationUrl + "/api/locations/nearby?lat={a}&lng={b}&radiusKm={c}&limit={d}",
                            lat, lng, searchRadiusKm, maxCandidates)
                    .retrieve().body(List.class);
            List<CandidateDriver> out = new ArrayList<>();
            if (raw == null) return out;
            for (Object o : raw) {
                Map<String, Object> m = (Map<String, Object>) o;
                out.add(new CandidateDriver(
                        String.valueOf(m.get("driverId")),
                        ((Number) m.getOrDefault("lat", 0.0)).doubleValue(),
                        ((Number) m.getOrDefault("lng", 0.0)).doubleValue(),
                        ((Number) m.getOrDefault("distanceKm", 0.0)).doubleValue()
                ));
            }
            return out;
        } catch (Exception e) {
            log.warn("fetchNearbyDrivers failed: {}", e.getMessage());
            return List.of();
        }
    }

    @Override
    public boolean tryLockDriver(String driverId, int ttlSeconds) {
        String token = lock.tryLockDriver(driverId, Duration.ofSeconds(ttlSeconds));
        if (token != null) {
            // stash the token in Redis under a companion key so releaseDriverLock() can find it.
            lock.rememberToken(driverId, token);
            return true;
        }
        return false;
    }

    @Override
    public void releaseDriverLock(String driverId) {
        String tok = lock.recallToken(driverId);
        if (tok != null) {
            lock.release(driverId, tok);
            lock.forgetToken(driverId);
        }
    }

    @Override
    public void notifyDriver(String driverId, String rideId, int windowSeconds) {
        try {
            restClient.post().uri(notificationUrl + "/api/notifications/ride-request")
                    .body(Map.of(
                            "driverId", driverId,
                            "rideId", rideId,
                            "acceptanceWindowSeconds", windowSeconds
                    )).retrieve().toBodilessEntity();
        } catch (Exception e) {
            log.warn("notifyDriver({}, {}) failed: {}", driverId, rideId, e.getMessage());
        }
    }

    @Override
    public void notifyNoDrivers(String rideId) {
        try {
            restClient.post().uri(notificationUrl + "/api/notifications/ride-no-drivers")
                    .body(Map.of("rideId", rideId)).retrieve().toBodilessEntity();
        } catch (Exception ignored) {}
    }
}
