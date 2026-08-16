package com.uberclone.payments.repo;

import com.uberclone.payments.domain.Payment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PaymentRepository extends JpaRepository<Payment, UUID> {
    Optional<Payment> findByRideId(UUID rideId);
    List<Payment> findByDriverIdAndCreatedAtAfterOrderByCreatedAtDesc(String driverId, Instant after);
    List<Payment> findByRiderIdOrderByCreatedAtDesc(String riderId);
}
