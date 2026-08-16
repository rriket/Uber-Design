package com.uberclone.ride.domain;

import com.uberclone.common.dto.RideCategory;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "fares")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Fare {
    @Id
    @GeneratedValue
    private UUID id;

    @Column(nullable = false)
    private String userId; // Keycloak sub / username

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private RideCategory category;

    @Column(nullable = false) private Double pickupLat;
    @Column(nullable = false) private Double pickupLng;
    @Column(nullable = false) private String pickupAddress;

    @Column(nullable = false) private Double destLat;
    @Column(nullable = false) private Double destLng;
    @Column(nullable = false) private String destAddress;

    @Column(nullable = false) private Double distanceKm;

    /** In minutes. */
    @Column(nullable = false) private Integer etaMinutes;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal price;

    @Column(nullable = false)
    private String currency;

    /** True once the rider has confirmed this fare into a Ride. */
    @Column(nullable = false)
    private boolean consumed = false;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private Instant createdAt;
}
