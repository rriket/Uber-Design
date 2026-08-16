package com.uberclone.matching.web;

import com.uberclone.matching.config.TemporalConfig;
import com.uberclone.matching.workflow.MatchingWorkflow;
import com.uberclone.matching.workflow.ScheduledRideWorkflow;
import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowOptions;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/matching")
@RequiredArgsConstructor
public class MatchingController {

    private final WorkflowClient workflowClient;

    /** Kicks off a durable Temporal matching workflow. */
    @PostMapping("/internal/enqueue")
    public Map<String, Object> enqueue(@RequestBody Map<String, String> body) {
        UUID rideId = UUID.fromString(body.get("rideId"));
        String workflowId = "ride-match-" + rideId;

        MatchingWorkflow workflow = workflowClient.newWorkflowStub(
                MatchingWorkflow.class,
                WorkflowOptions.newBuilder()
                        .setTaskQueue(TemporalConfig.TASK_QUEUE)
                        .setWorkflowId(workflowId)
                        .setWorkflowExecutionTimeout(Duration.ofMinutes(5))
                        .build()
        );
        WorkflowClient.start(workflow::matchRide, rideId.toString());
        log.info("Enqueued Temporal matching workflow {} for ride {}", workflowId, rideId);
        return Map.of("status", "enqueued", "rideId", rideId.toString(), "workflowId", workflowId);
    }

    /**
     * Starts a durable timer workflow that will activate the given ride at scheduledFor.
     */
    @PostMapping("/internal/schedule")
    public Map<String, Object> schedule(@RequestBody Map<String, String> body) {
        UUID rideId = UUID.fromString(body.get("rideId"));
        Instant scheduledFor = Instant.parse(body.get("scheduledFor"));
        String workflowId = "ride-schedule-" + rideId;

        ScheduledRideWorkflow workflow = workflowClient.newWorkflowStub(
                ScheduledRideWorkflow.class,
                WorkflowOptions.newBuilder()
                        .setTaskQueue(TemporalConfig.TASK_QUEUE)
                        .setWorkflowId(workflowId)
                        // Allow the timer to sleep for up to 30 days
                        .setWorkflowExecutionTimeout(Duration.ofDays(31))
                        .build()
        );
        WorkflowClient.start(workflow::waitAndFire, rideId.toString(), scheduledFor);
        log.info("Enqueued Temporal scheduling workflow {} for ride {} (fires at {})", workflowId, rideId, scheduledFor);
        return Map.of("status", "scheduled", "rideId", rideId.toString(),
                "scheduledFor", scheduledFor.toString(), "workflowId", workflowId);
    }

    @GetMapping("/health-check")
    @PreAuthorize("permitAll()")
    public Map<String, String> health() { return Map.of("status", "ok"); }
}
