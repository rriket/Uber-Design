package com.uberclone.matching.workflow;

import io.temporal.workflow.WorkflowInterface;
import io.temporal.workflow.WorkflowMethod;

import java.time.Instant;

/**
 * Durable timer workflow. Sleeps until `scheduledFor`, then calls back
 * into ride-service to transition the ride from SCHEDULED → REQUESTED,
 * which in turn kicks off the normal MatchingWorkflow.
 *
 * Because Temporal persists the timer, this workflow survives service
 * restarts (unlike a stock ScheduledExecutorService).
 */
@WorkflowInterface
public interface ScheduledRideWorkflow {
    @WorkflowMethod
    void waitAndFire(String rideId, Instant scheduledFor);
}
