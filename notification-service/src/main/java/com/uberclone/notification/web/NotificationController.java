package com.uberclone.notification.web;

import com.uberclone.notification.ws.DriverWebSocketHandler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final DriverWebSocketHandler wsHandler;

    /**
     * Called by Matching Service to push a ride-request to a specific driver.
     */
    @PostMapping("/ride-request")
    public Map<String, Object> rideRequest(@RequestBody Map<String, Object> body) {
        String driverId = String.valueOf(body.get("driverId"));
        String rideId = String.valueOf(body.get("rideId"));
        Integer windowSec = (Integer) body.getOrDefault("acceptanceWindowSeconds", 10);

        Map<String, Object> payload = Map.of(
                "type", "RIDE_REQUEST",
                "rideId", rideId,
                "acceptanceWindowSeconds", windowSec,
                "timestamp", Instant.now().toString()
        );
        boolean delivered = wsHandler.send(driverId, payload);
        log.info("RIDE_REQUEST -> driver={} rideId={} delivered={}", driverId, rideId, delivered);
        return Map.of("delivered", delivered);
    }

    /** Broadcast to rider (via a “rider room”—simplified: goes to all drivers for demo). */
    @PostMapping("/ride-no-drivers")
    public Map<String, Object> noDrivers(@RequestBody Map<String, Object> body) {
        wsHandler.broadcast(Map.of(
                "type", "NO_DRIVERS_FOUND",
                "rideId", body.get("rideId")
        ));
        return Map.of("status", "broadcast");
    }
}
