package com.uberclone.matching.workflow;

import io.temporal.activity.ActivityOptions;
import io.temporal.workflow.Workflow;
import org.slf4j.Logger;

import java.time.Duration;
import java.time.Instant;

public class ScheduledRideWorkflowImpl implements ScheduledRideWorkflow {

    private static final Logger log = Workflow.getLogger(ScheduledRideWorkflowImpl.class);

    private final SchedulingActivities activities = Workflow.newActivityStub(
            SchedulingActivities.class,
            ActivityOptions.newBuilder()
                    .setStartToCloseTimeout(Duration.ofSeconds(15))
                    .build()
    );

    @Override
    public void waitAndFire(String rideId, Instant scheduledFor) {
        long delayMs = scheduledFor.toEpochMilli() - Workflow.currentTimeMillis();
        if (delayMs > 0) {
            log.info("Sleeping {} ms until scheduled pickup for ride {}", delayMs, rideId);
            Workflow.sleep(Duration.ofMillis(delayMs));
        }
        activities.activateRide(rideId);
        log.info("Scheduled ride {} activated", rideId);
    }
}
