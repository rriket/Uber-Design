package com.uberclone.payments.domain;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "payments", indexes = {
        @Index(name = "idx_pay_ride", columnList = "ride_id", unique = true),
        @Index(name = "idx_pay_driver", columnList = "driver_id"),
        @Index(name = "idx_pay_rider", columnList = "rider_id")
})
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Payment {
    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "ride_id", nullable = false)
    private UUID rideId;

    @Column(name = "rider_id", nullable = false)
    private String riderId;

    @Column(name = "driver_id", nullable = false)
    private String driverId;

    /** Total charged to the rider (USD). */
    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal amount;

    /** Driver's cut (80% by default). */
    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal driverEarnings;

    /** Platform's cut (20%). */
    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal platformFee;

    @Column(nullable = false, length = 8)
    private String currency;

    /** Stripe PaymentIntent id, once created. */
    @Column(name = "stripe_payment_intent_id", length = 128)
    private String stripePaymentIntentId;

    /**
     * PENDING - just created
     * SUCCEEDED - Stripe confirmed the charge
     * FAILED - charge failed
     * MOCKED - Stripe key not configured, recorded locally only
     */
    @Column(nullable = false, length = 16)
    private String status;

    @Column(length = 500)
    private String failureMessage;

    @CreationTimestamp @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp @Column(nullable = false)
    private Instant updatedAt;
}
