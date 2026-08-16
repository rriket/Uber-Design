package com.uberclone.ride.repo;

import com.uberclone.common.dto.RideStatus;
import com.uberclone.ride.domain.Ride;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RideRepository extends JpaRepository<Ride, UUID> {
    List<Ride> findByRiderIdOrderByCreatedAtDesc(String riderId);
    List<Ride> findByRiderIdAndStatusOrderByScheduledForAsc(String riderId, com.uberclone.common.dto.RideStatus status);
    List<Ride> findByDriverIdOrderByCreatedAtDesc(String driverId);
    Optional<Ride> findByRiderIdAndStatusIn(String riderId, List<RideStatus> statuses);
    Optional<Ride> findByDriverIdAndStatusIn(String driverId, List<RideStatus> statuses);
}
