package com.uberclone.matching.workflow;

import io.temporal.activity.ActivityInterface;
import io.temporal.activity.ActivityMethod;

import java.util.List;

@ActivityInterface
public interface MatchingActivities {

    @ActivityMethod
    RideSnapshot fetchRide(String rideId);

    @ActivityMethod
    List<CandidateDriver> fetchNearbyDrivers(double lat, double lng);

    @ActivityMethod
    boolean tryLockDriver(String driverId, int ttlSeconds);

    @ActivityMethod
    void releaseDriverLock(String driverId);

    @ActivityMethod
    void notifyDriver(String driverId, String rideId, int windowSeconds);

    /** Returns current status (e.g. "ACCEPTED") and assigned driver, if any. */
    @ActivityMethod
    RideSnapshot pollRide(String rideId);

    @ActivityMethod
    void notifyNoDrivers(String rideId);

    record RideSnapshot(String rideId, String status, String driverId, double pickupLat, double pickupLng) {}
    record CandidateDriver(String driverId, double lat, double lng, double distanceKm) {}
}
