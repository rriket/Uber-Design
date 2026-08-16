package com.uberclone.matching.workflow;

import io.temporal.activity.ActivityOptions;
import io.temporal.common.RetryOptions;
import io.temporal.workflow.Workflow;
import org.slf4j.Logger;

import java.time.Duration;
import java.util.List;

public class MatchingWorkflowImpl implements MatchingWorkflow {

    private static final Logger log = Workflow.getLogger(MatchingWorkflowImpl.class);

    // Note: acceptance-window / poll timings are effectively hard-coded here so the
    // workflow is deterministic. Change via a new workflow version if needed.
    private static final int ACCEPTANCE_WINDOW_SECONDS = 10;

    private final MatchingActivities activities = Workflow.newActivityStub(
            MatchingActivities.class,
            ActivityOptions.newBuilder()
                    .setStartToCloseTimeout(Duration.ofSeconds(15))
                    .setRetryOptions(RetryOptions.newBuilder()
                            .setMaximumAttempts(3)
                            .setInitialInterval(Duration.ofMillis(500))
                            .build())
                    .build()
    );

    @Override
    public void matchRide(String rideId) {
        log.info("Matching workflow started for ride {}", rideId);

        MatchingActivities.RideSnapshot ride = activities.fetchRide(rideId);
        if (ride == null) {
            log.warn("Ride {} not found", rideId);
            return;
        }

        List<MatchingActivities.CandidateDriver> candidates =
                activities.fetchNearbyDrivers(ride.pickupLat(), ride.pickupLng());
        log.info("Found {} candidate driver(s) for ride {}", candidates.size(), rideId);

        for (MatchingActivities.CandidateDriver cand : candidates) {
            if (tryDriver(rideId, cand.driverId())) return;
        }

        log.info("No drivers accepted ride {}", rideId);
        activities.notifyNoDrivers(rideId);
    }

    private boolean tryDriver(String rideId, String driverId) {
        boolean locked = activities.tryLockDriver(driverId, ACCEPTANCE_WINDOW_SECONDS);
        if (!locked) {
            log.info("Driver {} already busy, skipping", driverId);
            return false;
        }
        try {
            activities.notifyDriver(driverId, rideId, ACCEPTANCE_WINDOW_SECONDS);

            // Poll every 1s inside the acceptance window.
            for (int i = 0; i < ACCEPTANCE_WINDOW_SECONDS; i++) {
                Workflow.sleep(Duration.ofSeconds(1));
                MatchingActivities.RideSnapshot snap = activities.pollRide(rideId);
                if (snap == null) continue;
                if ("CANCELLED".equals(snap.status())) return true;
                if ("ACCEPTED".equals(snap.status()) && driverId.equals(snap.driverId())) {
                    log.info("Driver {} ACCEPTED ride {}", driverId, rideId);
                    return true;
                }
            }
            log.info("Driver {} timed out for ride {}", driverId, rideId);
            return false;
        } finally {
            activities.releaseDriverLock(driverId);
        }
    }
}
