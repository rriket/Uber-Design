package com.uberclone.common.dto;

/**
 * Lifecycle states of a Ride, mirroring the design document.
 */
public enum RideStatus {
    SCHEDULED,          // Rider booked a ride for later; Temporal timer is holding it
    REQUESTED,          // Rider confirmed fare, matching hasn't found a driver yet
    MATCHING,           // Ride is in the matching workflow (queue / distributed lock)
    ACCEPTED,           // A driver accepted the request; enroute to pickup
    IN_PROGRESS,        // Driver picked up the rider; enroute to destination
    COMPLETED,          // Successful drop-off
    PAID,               // Successful payment charged
    CANCELLED,          // Rider or system cancelled
    NO_DRIVERS_FOUND    // Matching exhausted all candidate drivers
}
