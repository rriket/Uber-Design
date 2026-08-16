package com.uberclone.ride.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.uberclone.common.dto.LatLng;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * Thin wrapper around Google Maps Distance Matrix API.
 *
 * If no API key is provided (or the API call fails), we gracefully fall
 * back to a haversine-distance heuristic so the app still works offline
 * / during local demo runs.
 */
@Slf4j
@Component
public class GoogleMapsClient {

    private static final String DISTANCE_MATRIX_URL =
            "https://maps.googleapis.com/maps/api/distancematrix/json";

    private final RestClient restClient = RestClient.create();
    private final String apiKey;

    public GoogleMapsClient(@Value("${google.maps.api-key:}") String apiKey) {
        this.apiKey = apiKey;
    }

    public record DistanceResult(double distanceKm, int etaMinutes, boolean mocked) {}

    public DistanceResult distanceMatrix(LatLng origin, LatLng destination) {
        if (apiKey == null || apiKey.isBlank()) {
            return fallback(origin, destination, "no api key configured");
        }

        try {
            String url = DISTANCE_MATRIX_URL
                    + "?origins=" + origin.lat() + "," + origin.lng()
                    + "&destinations=" + destination.lat() + "," + destination.lng()
                    + "&units=metric"
                    + "&key=" + apiKey;

            JsonNode body = restClient.get().uri(url).retrieve().body(JsonNode.class);
            if (body == null) return fallback(origin, destination, "empty body");

            JsonNode element = body.at("/rows/0/elements/0");
            if (element.isMissingNode() || !"OK".equals(element.path("status").asText())) {
                return fallback(origin, destination, "status=" + element.path("status").asText());
            }
            double distanceMeters = element.path("distance").path("value").asDouble();
            double durationSeconds = element.path("duration").path("value").asDouble();
            return new DistanceResult(distanceMeters / 1000.0, (int) Math.max(1, Math.round(durationSeconds / 60.0)), false);
        } catch (Exception e) {
            log.warn("Google Maps DistanceMatrix call failed: {}", e.getMessage());
            return fallback(origin, destination, e.getMessage());
        }
    }

    private DistanceResult fallback(LatLng origin, LatLng destination, String reason) {
        double km = LatLng.distanceKm(origin, destination);
        int minutes = (int) Math.max(1, Math.round((km / 30.0) * 60.0)); // assume 30 km/h avg city speed
        log.info("Using haversine fallback (reason={}): {}km, {}min", reason, km, minutes);
        return new DistanceResult(km, minutes, true);
    }
}
