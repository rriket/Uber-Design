package com.uberclone.matching.workflow;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.Map;

@Slf4j
@Component
public class SchedulingActivitiesImpl implements SchedulingActivities {

    private final RestClient restClient = RestClient.create();

    @Value("${uber.services.ride-url:http://localhost:8081}")
    private String rideUrl;

    @Override
    public void activateRide(String rideId) {
        try {
            restClient.post()
                    .uri(rideUrl + "/api/rides/internal/" + rideId + "/activate")
                    .body(Map.of())
                    .retrieve().toBodilessEntity();
        } catch (Exception e) {
            log.error("activateRide({}) failed: {}", rideId, e.getMessage());
            throw new RuntimeException(e);
        }
    }
}
