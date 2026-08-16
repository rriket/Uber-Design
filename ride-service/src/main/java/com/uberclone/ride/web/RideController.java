package com.uberclone.ride.web;

import com.uberclone.common.dto.RideStatus;
import com.uberclone.ride.dto.*;
import com.uberclone.ride.service.RideService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/rides")
@RequiredArgsConstructor
@Tag(name = "Rides", description = "Ride lifecycle endpoints")
public class RideController {

    private final RideService rideService;

    @PostMapping
    @PreAuthorize("hasRole('RIDER')")
    public RideResponse requestRide(@AuthenticationPrincipal Jwt jwt,
                                    @RequestBody @Valid RequestRideRequest req) {
        return rideService.requestRide(jwt.getClaimAsString("preferred_username"), req);
    }

    @PostMapping("/schedule")
    @PreAuthorize("hasRole('RIDER')")
    public RideResponse scheduleRide(@AuthenticationPrincipal Jwt jwt,
                                     @RequestBody @Valid ScheduleRideRequest req) {
        return rideService.scheduleRide(jwt.getClaimAsString("preferred_username"), req);
    }

    @GetMapping("/scheduled/mine")
    @PreAuthorize("hasRole('RIDER')")
    public List<RideResponse> myScheduledRides(@AuthenticationPrincipal Jwt jwt) {
        return rideService.myScheduledRides(jwt.getClaimAsString("preferred_username"));
    }

    /**
     * Called ONLY by the Temporal ScheduledRideWorkflow when the timer fires.
     * In production this would be secured with service auth / mTLS.
     */
    @PostMapping("/internal/{rideId}/activate")
    public RideResponse activateScheduledRide(@PathVariable UUID rideId) {
        return rideService.activateScheduledRide(rideId);
    }

    @GetMapping("/{rideId}")
    public RideResponse getRide(@PathVariable UUID rideId) {
        return rideService.getRide(rideId);
    }

    @GetMapping("/mine")
    @PreAuthorize("hasRole('RIDER')")
    public List<RideResponse> myRiderRides(@AuthenticationPrincipal Jwt jwt) {
        return rideService.ridesForRider(jwt.getClaimAsString("preferred_username"));
    }

    @GetMapping("/driver/mine")
    @PreAuthorize("hasRole('DRIVER')")
    public List<RideResponse> myDriverRides(@AuthenticationPrincipal Jwt jwt) {
        return rideService.ridesForDriver(jwt.getClaimAsString("preferred_username"));
    }

    @PatchMapping("/{rideId}/accept")
    @PreAuthorize("hasRole('DRIVER')")
    public RideResponse accept(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID rideId) {
        return rideService.acceptRide(rideId, jwt.getClaimAsString("preferred_username"));
    }

    @PatchMapping("/{rideId}/decline")
    @PreAuthorize("hasRole('DRIVER')")
    public RideResponse decline(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID rideId) {
        return rideService.declineRide(rideId, jwt.getClaimAsString("preferred_username"));
    }

    @PatchMapping("/{rideId}/status")
    @PreAuthorize("hasRole('DRIVER')")
    public RideResponse updateStatus(@AuthenticationPrincipal Jwt jwt,
                                     @PathVariable UUID rideId,
                                     @RequestBody Map<String, String> body) {
        RideStatus target = RideStatus.valueOf(body.get("status"));
        return rideService.updateStatus(rideId, jwt.getClaimAsString("preferred_username"), target);
    }
}
