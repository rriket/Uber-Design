package com.uberclone.payments.web;

import com.uberclone.payments.dto.*;
import com.uberclone.payments.service.PaymentsService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/payments")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentsService service;

    /** Called by ride-service on ride completion (server-to-server). */
    @PostMapping("/internal/charge")
    public PaymentResponse chargeRide(@RequestBody @Valid ChargeRideRequest req) {
        return service.chargeRide(req);
    }

    @GetMapping("/ride/{rideId}")
    public PaymentResponse forRide(@PathVariable UUID rideId) {
        return service.forRide(rideId);
    }

    @GetMapping("/rider/mine")
    @PreAuthorize("hasRole('RIDER')")
    public List<PaymentResponse> myRiderPayments(@AuthenticationPrincipal Jwt jwt) {
        return service.forRider(jwt.getClaimAsString("preferred_username"));
    }

    @GetMapping("/driver/mine/earnings")
    @PreAuthorize("hasRole('DRIVER')")
    public EarningsSummary myEarnings(@AuthenticationPrincipal Jwt jwt,
                                      @RequestParam(defaultValue = "7") int days) {
        return service.driverEarnings(jwt.getClaimAsString("preferred_username"), days);
    }
}
