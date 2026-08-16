package com.uberclone.ride.repo;

import com.uberclone.ride.domain.Fare;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface FareRepository extends JpaRepository<Fare, UUID> {
}
