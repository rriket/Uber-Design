package com.uberclone.ratings.domain;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

/**
 * One row per (ride, direction). "direction" is captured by ratedRole:
 *   ratedRole=DRIVER means the rider (ratedByUserId) rated the driver (ratedUserId)
 *   ratedRole=RIDER  means the driver rated the rider
 */
@Entity
@Table(name = "ratings",
        uniqueConstraints = @UniqueConstraint(name = "uk_ride_direction", columnNames = {"ride_id", "rated_role"}),
        indexes = {
                @Index(name = "idx_rated_user", columnList = "rated_user_id, rated_role")
        })
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Rating {
    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "ride_id", nullable = false)
    private UUID rideId;

    /** Who wrote the rating (Keycloak preferred_username). */
    @Column(name = "rated_by_user_id", nullable = false)
    private String ratedByUserId;

    /** Who was rated. */
    @Column(name = "rated_user_id", nullable = false)
    private String ratedUserId;

    /** Role of the rated user — DRIVER or RIDER. */
    @Column(name = "rated_role", nullable = false, length = 16)
    private String ratedRole;

    @Column(nullable = false)
    private Integer stars; // 1..5

    @Column(length = 500)
    private String comment;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private Instant createdAt;
}
