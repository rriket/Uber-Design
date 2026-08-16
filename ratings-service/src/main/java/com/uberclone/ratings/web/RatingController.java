package com.uberclone.ratings.web;

import com.uberclone.ratings.dto.*;
import com.uberclone.ratings.service.RatingsService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/ratings")
@RequiredArgsConstructor
public class RatingController {

    private final RatingsService service;

    @PostMapping
    public RatingResponse submit(@AuthenticationPrincipal Jwt jwt,
                                 @RequestBody @Valid SubmitRatingRequest req) {
        String userId = jwt.getClaimAsString("preferred_username");
        return service.submit(userId, req);
    }

    @GetMapping("/ride/{rideId}")
    public List<RatingResponse> forRide(@PathVariable UUID rideId) {
        return service.forRide(rideId);
    }

    @GetMapping("/user/{userId}/summary")
    public UserRatingSummary summary(@PathVariable String userId,
                                     @RequestParam(defaultValue = "DRIVER") String role) {
        return service.summary(userId, role);
    }

    @GetMapping("/user/{userId}/history")
    public List<RatingResponse> history(@PathVariable String userId,
                                        @RequestParam(defaultValue = "DRIVER") String role) {
        return service.historyFor(userId, role);
    }
}
