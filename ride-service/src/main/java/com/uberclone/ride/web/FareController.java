package com.uberclone.ride.web;

import com.uberclone.ride.dto.FareEstimateRequest;
import com.uberclone.ride.dto.FareEstimateResponse;
import com.uberclone.ride.service.RideService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/fares")
@RequiredArgsConstructor
@Tag(name = "Fares", description = "Fare estimation endpoints")
public class FareController {

    private final RideService rideService;

    @PostMapping("/estimate")
    @PreAuthorize("hasRole('RIDER')")
    public FareEstimateResponse estimate(@AuthenticationPrincipal Jwt jwt,
                                         @RequestBody @Valid FareEstimateRequest req) {
        String userId = jwt.getClaimAsString("preferred_username");
        return rideService.estimateFare(userId, req);
    }
}
