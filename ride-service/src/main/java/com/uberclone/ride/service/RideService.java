package com.uberclone.ride.service;

import com.uberclone.common.dto.LatLng;
import com.uberclone.common.dto.RideCategory;
import com.uberclone.common.dto.RideStatus;
import com.uberclone.ride.domain.Fare;
import com.uberclone.ride.domain.Ride;
import com.uberclone.ride.dto.*;
import com.uberclone.ride.integration.GoogleMapsClient;
import com.uberclone.ride.events.RideEventPublisher;
import com.uberclone.ride.repo.FareRepository;
import com.uberclone.ride.repo.RideRepository;
import com.uberclone.ride.surge.SurgePricingService;
import com.uberclone.ride.surge.SurgePricingService.SurgeInfo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class RideService {

    private final FareRepository fareRepo;
    private final RideRepository rideRepo;
    private final GoogleMapsClient mapsClient;
    private final SurgePricingService surge;
    private final RideEventPublisher events;

    @Value("${uber.pricing.base-fare:2.50}") private BigDecimal baseFare;
    @Value("${uber.pricing.per-km:1.75}")    private BigDecimal perKm;
    @Value("${uber.pricing.per-minute:0.30}") private BigDecimal perMinute;
    @Value("${uber.pricing.currency:USD}")   private String currency;

    @Value("${uber.services.matching-url:http://localhost:8083}") private String matchingUrl;
    @Value("${uber.services.payments-url:http://localhost:8086}") private String paymentsUrl;

    @SuppressWarnings("unused")
    private final RestClient restClient = RestClient.create();

    /* ---------------- Fare estimate (multi-tier + surge) ---------------- */

    @Transactional
    public FareEstimateResponse estimateFare(String userId, FareEstimateRequest req) {
        var pickup = new LatLng(req.pickup().lat(), req.pickup().lng());
        var dest = new LatLng(req.destination().lat(), req.destination().lng());

        var result = mapsClient.distanceMatrix(pickup, dest);
        double distKm = round2(result.distanceKm());

        surge.recordRequest(pickup);
        SurgeInfo surgeInfo = surge.compute(pickup);
        BigDecimal surgeMult = BigDecimal.valueOf(surgeInfo.multiplier());

        List<FareOption> options = new ArrayList<>();
        for (RideCategory cat : RideCategory.values()) {
            int eta = result.etaMinutes() + cat.etaExtraMinutes();
            BigDecimal base = baseFare
                    .add(perKm.multiply(BigDecimal.valueOf(distKm)))
                    .add(perMinute.multiply(BigDecimal.valueOf(eta)));
            BigDecimal price = base
                    .multiply(BigDecimal.valueOf(cat.priceMultiplier()))
                    .multiply(surgeMult)
                    .setScale(2, RoundingMode.HALF_UP);

            Fare fare = Fare.builder()
                    .userId(userId).category(cat)
                    .pickupLat(pickup.lat()).pickupLng(pickup.lng()).pickupAddress(req.pickupAddress())
                    .destLat(dest.lat()).destLng(dest.lng()).destAddress(req.destinationAddress())
                    .distanceKm(distKm)
                    .etaMinutes(eta)
                    .price(price)
                    .currency(currency)
                    .consumed(false)
                    .build();
            fare = fareRepo.save(fare);

            options.add(new FareOption(
                    fare.getId(), cat, labelFor(cat), descriptionFor(cat), capacityFor(cat),
                    price, currency, eta, distKm
            ));
        }

        return new FareEstimateResponse(
                req.pickupAddress(), req.destinationAddress(),
                distKm, result.etaMinutes(), result.mocked(),
                surgeInfo.multiplier(), surgeInfo.band(),
                options
        );
    }

    private String labelFor(RideCategory c) {
        return switch (c) { case UBER_X -> "UberX"; case UBER_XL -> "UberXL"; case UBER_COMFORT -> "Comfort"; };
    }
    private String descriptionFor(RideCategory c) {
        return switch (c) {
            case UBER_X -> "Affordable, everyday rides";
            case UBER_XL -> "Room for 6, spacious rides";
            case UBER_COMFORT -> "Newer cars, top-rated drivers";
        };
    }
    private int capacityFor(RideCategory c) {
        return switch (c) { case UBER_X -> 4; case UBER_XL -> 6; case UBER_COMFORT -> 4; };
    }

    /* ---------------- Request Ride (immediate) ---------------- */

    @Transactional
    public RideResponse requestRide(String riderId, RequestRideRequest req) {
        Ride ride = createRideFromFare(riderId, req.fareId(), null);
        ride.setStatus(RideStatus.REQUESTED);
        ride = rideRepo.save(ride);
        events.publishRequested(ride);
        return toDto(ride);
    }

    /* ---------------- Schedule a Ride ---------------- */

    @Transactional
    public RideResponse scheduleRide(String riderId, ScheduleRideRequest req) {
        if (req.scheduledFor().isBefore(Instant.now().plusSeconds(30)))
            throw new IllegalArgumentException("scheduledFor must be at least 30 seconds in the future");

        Ride ride = createRideFromFare(riderId, req.fareId(), req.scheduledFor());
        ride.setStatus(RideStatus.SCHEDULED);
        ride = rideRepo.save(ride);
        events.publishScheduled(ride);
        return toDto(ride);
    }

    /** Called by the ScheduledRideWorkflow when the timer fires. */
    @Transactional
    public RideResponse activateScheduledRide(UUID rideId) {
        Ride ride = rideRepo.findById(rideId)
                .orElseThrow(() -> new IllegalArgumentException("Ride not found"));
        if (ride.getStatus() != RideStatus.SCHEDULED)
            throw new IllegalStateException("Ride is not in SCHEDULED state: " + ride.getStatus());
        ride.setStatus(RideStatus.REQUESTED);
        rideRepo.save(ride);
        events.publishRequested(ride);
        return toDto(ride);
    }

    public List<RideResponse> myScheduledRides(String riderId) {
        return rideRepo.findByRiderIdAndStatusOrderByScheduledForAsc(riderId, RideStatus.SCHEDULED)
                .stream().map(this::toDto).toList();
    }

    /* ---------------- Ride creation helper ---------------- */

    private Ride createRideFromFare(String riderId, UUID fareId, Instant scheduledFor) {
        Fare fare = fareRepo.findById(fareId)
                .orElseThrow(() -> new IllegalArgumentException("Fare not found"));
        if (!fare.getUserId().equals(riderId))
            throw new IllegalArgumentException("Fare does not belong to this rider");
        if (fare.isConsumed())
            throw new IllegalStateException("Fare already used for a ride");

        // For scheduled rides, allow multiple bookings. For immediate, block if active.
        if (scheduledFor == null) {
            rideRepo.findByRiderIdAndStatusIn(riderId,
                    List.of(RideStatus.REQUESTED, RideStatus.MATCHING, RideStatus.ACCEPTED, RideStatus.IN_PROGRESS))
                    .ifPresent(r -> { throw new IllegalStateException("You already have an active ride: " + r.getId()); });
        }

        Ride ride = Ride.builder()
                .riderId(riderId)
                .fareId(fare.getId())
                .category(fare.getCategory())
                .pickupLat(fare.getPickupLat()).pickupLng(fare.getPickupLng()).pickupAddress(fare.getPickupAddress())
                .destLat(fare.getDestLat()).destLng(fare.getDestLng()).destAddress(fare.getDestAddress())
                .quotedPrice(fare.getPrice())
                .scheduledFor(scheduledFor)
                .build();

        fare.setConsumed(true);
        fareRepo.save(fare);
        return ride;
    }

    /* ---------------- Accept / Decline / Progress ---------------- */

    @Transactional
    public RideResponse acceptRide(UUID rideId, String driverId) {
        Ride ride = rideRepo.findById(rideId)
                .orElseThrow(() -> new IllegalArgumentException("Ride not found"));
        if (ride.getStatus() != RideStatus.REQUESTED && ride.getStatus() != RideStatus.MATCHING)
            throw new IllegalStateException("Ride cannot be accepted in state " + ride.getStatus());

        rideRepo.findByDriverIdAndStatusIn(driverId,
                List.of(RideStatus.ACCEPTED, RideStatus.IN_PROGRESS))
                .ifPresent(r -> { throw new IllegalStateException("Driver already on a ride: " + r.getId()); });

        ride.setDriverId(driverId);
        ride.setStatus(RideStatus.ACCEPTED);
        ride.setAcceptedAt(Instant.now());
        Ride saved = rideRepo.save(ride);
        events.publishAccepted(saved);
        return toDto(saved);
    }

    @Transactional
    public RideResponse declineRide(UUID rideId, String driverId) {
        Ride ride = rideRepo.findById(rideId)
                .orElseThrow(() -> new IllegalArgumentException("Ride not found"));
        log.info("Driver {} declined ride {}", driverId, rideId);
        return toDto(ride);
    }

    @Transactional
    public RideResponse updateStatus(UUID rideId, String actorId, RideStatus newStatus) {
        Ride ride = rideRepo.findById(rideId)
                .orElseThrow(() -> new IllegalArgumentException("Ride not found"));
        if (ride.getDriverId() == null || !ride.getDriverId().equals(actorId))
            throw new IllegalArgumentException("Only the assigned driver can update this ride");

        switch (newStatus) {
            case IN_PROGRESS -> {
                if (ride.getStatus() != RideStatus.ACCEPTED)
                    throw new IllegalStateException("Ride must be ACCEPTED before IN_PROGRESS");
                ride.setPickedUpAt(Instant.now());
            }
            case COMPLETED -> {
                if (ride.getStatus() != RideStatus.IN_PROGRESS)
                    throw new IllegalStateException("Ride must be IN_PROGRESS before COMPLETED");
                ride.setCompletedAt(Instant.now());
                ride.setFinalPrice(ride.getQuotedPrice());
            }
            case CANCELLED -> ride.setCompletedAt(Instant.now());
            default -> throw new IllegalArgumentException("Illegal transition target: " + newStatus);
        }
        ride.setStatus(newStatus);
        Ride saved = rideRepo.save(ride);

        // Publish domain event; payments-service (and future analytics/notification)
        // consume via Kafka — no more tight REST coupling.
        if (newStatus == RideStatus.COMPLETED) {
            events.publishCompleted(saved);
        } else if (newStatus == RideStatus.CANCELLED) {
            events.publishCancelled(saved, actorId);
        }
        return toDto(saved);
    }

    /* ---------------- Queries ---------------- */

    public RideResponse getRide(UUID rideId) {
        return rideRepo.findById(rideId).map(this::toDto)
                .orElseThrow(() -> new IllegalArgumentException("Ride not found"));
    }
    public List<RideResponse> ridesForRider(String riderId) {
        return rideRepo.findByRiderIdOrderByCreatedAtDesc(riderId).stream().map(this::toDto).toList();
    }
    public List<RideResponse> ridesForDriver(String driverId) {
        return rideRepo.findByDriverIdOrderByCreatedAtDesc(driverId).stream().map(this::toDto).toList();
    }

    /* ---------------- Downstream calls ---------------- */

    private RideResponse toDto(Ride r) {
        return new RideResponse(
                r.getId(), r.getRiderId(), r.getDriverId(), r.getFareId(), r.getCategory(),
                r.getPickupLat(), r.getPickupLng(), r.getPickupAddress(),
                r.getDestLat(), r.getDestLng(), r.getDestAddress(),
                r.getQuotedPrice(), r.getFinalPrice(), r.getStatus(),
                r.getCreatedAt(), r.getAcceptedAt(), r.getPickedUpAt(), r.getCompletedAt(), r.getScheduledFor()
        );
    }

    private double round2(double d) { return Math.round(d * 100.0) / 100.0; }
}
