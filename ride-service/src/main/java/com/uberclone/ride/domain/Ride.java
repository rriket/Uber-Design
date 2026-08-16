package com.uberclone.ride.domain;

import com.uberclone.common.dto.RideCategory;
import com.uberclone.common.dto.RideStatus;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "rides", indexes = {
        @Index(name = "idx_ride_rider", columnList = "rider_id"),
        @Index(name = "idx_ride_driver", columnList = "driver_id"),
        @Index(name = "idx_ride_status", columnList = "status")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Ride {
    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "rider_id", nullable = false)
    private String riderId;

    @Column(name = "driver_id")
    private String driverId;

    @Column(name = "fare_id", nullable = false)
    private UUID fareId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private RideCategory category;

    @Column(nullable = false) private Double pickupLat;
    @Column(nullable = false) private Double pickupLng;
    @Column(nullable = false) private String pickupAddress;

    @Column(nullable = false) private Double destLat;
    @Column(nullable = false) private Double destLng;
    @Column(nullable = false) private String destAddress;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal quotedPrice;

    @Column(precision = 10, scale = 2)
    private BigDecimal finalPrice;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private RideStatus status;

    private Instant acceptedAt;
    private Instant pickedUpAt;
    private Instant completedAt;

    /** For scheduled rides — the pickup time in the future. */
    private Instant scheduledFor;

    @CreationTimestamp @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp @Column(nullable = false)
    private Instant updatedAt;
}
