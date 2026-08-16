package com.uberclone.ratings.repo;

import com.uberclone.ratings.domain.Rating;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RatingRepository extends JpaRepository<Rating, UUID> {

    Optional<Rating> findByRideIdAndRatedRole(UUID rideId, String ratedRole);
    List<Rating> findByRideId(UUID rideId);
    List<Rating> findByRatedUserIdAndRatedRoleOrderByCreatedAtDesc(String ratedUserId, String ratedRole);

    @Query("select coalesce(avg(r.stars), 0) from Rating r where r.ratedUserId = ?1 and r.ratedRole = ?2")
    Double avgStars(String ratedUserId, String ratedRole);

    long countByRatedUserIdAndRatedRole(String ratedUserId, String ratedRole);
}
