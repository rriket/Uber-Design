package com.uberclone.matching.workflow;

import io.temporal.workflow.WorkflowInterface;
import io.temporal.workflow.WorkflowMethod;

/**
 * Temporal workflow contract for matching a Ride to a Driver.
 *
 * Durability: If the matching-service process crashes mid-workflow,
 * Temporal replays the workflow from its persisted event history on any
 * available worker — the ride is NEVER dropped.
 */
@WorkflowInterface
public interface MatchingWorkflow {
    @WorkflowMethod
    void matchRide(String rideId);
}
