package com.uberclone.location.web;

import com.uberclone.common.dto.LatLng;
import com.uberclone.location.service.LocationService;
import com.uberclone.location.service.LocationService.NearbyDriver;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/locations")
@RequiredArgsConstructor
public class LocationController {

    private final LocationService service;

    /** Driver heartbeat: update current location. */
    @PostMapping("/driver")
    @PreAuthorize("hasRole('DRIVER')")
    public void updateDriverLocation(@AuthenticationPrincipal Jwt jwt,
                                     @RequestBody @Valid LatLng loc) {
        service.updateLocation(jwt.getClaimAsString("preferred_username"), loc);
    }

    @DeleteMapping("/driver")
    @PreAuthorize("hasRole('DRIVER')")
    public void goOffline(@AuthenticationPrincipal Jwt jwt) {
        service.goOffline(jwt.getClaimAsString("preferred_username"));
    }

    @GetMapping("/driver/{driverId}")
    public LatLng getDriverLocation(@PathVariable String driverId) {
        return service.getLocation(driverId);
    }

    /** Nearby driver search - typically called by matching service or rider tracking. */
    @GetMapping("/nearby")
    public List<NearbyDriver> nearby(@RequestParam double lat,
                                     @RequestParam double lng,
                                     @RequestParam(defaultValue = "5") double radiusKm,
                                     @RequestParam(defaultValue = "10") int limit) {
        return service.findNearby(new LatLng(lat, lng), radiusKm, limit);
    }
}
