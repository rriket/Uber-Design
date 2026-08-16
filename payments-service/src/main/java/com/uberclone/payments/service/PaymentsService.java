package com.uberclone.payments.service;

import com.uberclone.payments.domain.Payment;
import com.uberclone.payments.dto.*;
import com.uberclone.payments.integration.StripeClient;
import com.uberclone.payments.repo.PaymentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentsService {

    private final PaymentRepository repo;
    private final StripeClient stripe;

    @Value("${uber.payments.driver-share:0.80}")
    private BigDecimal driverShare;

    /* -------- Charge on ride complete (called by ride-service) -------- */

    @Transactional
    public PaymentResponse chargeRide(ChargeRideRequest req) {
        // Idempotent: if a payment for this ride already exists, return it.
        Optional<Payment> existing = repo.findByRideId(req.rideId());
        if (existing.isPresent()) {
            log.info("Payment for ride {} already exists — returning existing", req.rideId());
            return toDto(existing.get());
        }

        BigDecimal amount = req.amount().setScale(2, RoundingMode.HALF_UP);
        BigDecimal driverCut = amount.multiply(driverShare).setScale(2, RoundingMode.HALF_UP);
        BigDecimal platformCut = amount.subtract(driverCut);

        Payment p = Payment.builder()
                .rideId(req.rideId()).riderId(req.riderId()).driverId(req.driverId())
                .amount(amount).driverEarnings(driverCut).platformFee(platformCut)
                .currency(req.currency()).status("PENDING")
                .build();
        p = repo.save(p);

        StripeClient.ChargeResult result = stripe.createCharge(
                req.rideId().toString(), amount, req.currency(), req.riderId(), req.driverId());
        p.setStatus(result.status());
        p.setStripePaymentIntentId(result.paymentIntentId());
        p.setFailureMessage(result.failureMessage());
        p = repo.save(p);
        return toDto(p);
    }

    /* -------- Queries -------- */

    public PaymentResponse forRide(UUID rideId) {
        return repo.findByRideId(rideId).map(this::toDto).orElse(null);
    }

    public List<PaymentResponse> forRider(String riderId) {
        return repo.findByRiderIdOrderByCreatedAtDesc(riderId).stream().map(this::toDto).toList();
    }

    /* -------- Driver earnings dashboard -------- */

    public EarningsSummary driverEarnings(String driverId, int days) {
        Instant since = Instant.now().minus(days, ChronoUnit.DAYS);
        List<Payment> ps = repo.findByDriverIdAndCreatedAtAfterOrderByCreatedAtDesc(driverId, since);

        BigDecimal total = ps.stream()
                .filter(p -> !"FAILED".equals(p.getStatus()))
                .map(Payment::getDriverEarnings)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // Group by local date (UTC for simplicity)
        Map<LocalDate, List<Payment>> byDay = ps.stream()
                .filter(p -> !"FAILED".equals(p.getStatus()))
                .collect(Collectors.groupingBy(p -> LocalDate.ofInstant(p.getCreatedAt(), ZoneOffset.UTC)));

        List<EarningsSummary.DailyBucket> daily = new ArrayList<>();
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        for (int i = days - 1; i >= 0; i--) {
            LocalDate d = today.minusDays(i);
            List<Payment> list = byDay.getOrDefault(d, List.of());
            BigDecimal sum = list.stream().map(Payment::getDriverEarnings).reduce(BigDecimal.ZERO, BigDecimal::add);
            daily.add(new EarningsSummary.DailyBucket(d, sum, list.size()));
        }
        return new EarningsSummary(driverId, days, total, ps.size(), daily);
    }

    private PaymentResponse toDto(Payment p) {
        return new PaymentResponse(p.getId(), p.getRideId(), p.getRiderId(), p.getDriverId(),
                p.getAmount(), p.getDriverEarnings(), p.getPlatformFee(),
                p.getCurrency(), p.getStatus(), p.getStripePaymentIntentId(),
                p.getFailureMessage(), p.getCreatedAt(), p.getUpdatedAt());
    }
}
