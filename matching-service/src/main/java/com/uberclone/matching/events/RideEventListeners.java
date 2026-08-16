package com.uberclone.matching.events;

import com.uberclone.common.events.RideEvents.RideRequested;
import com.uberclone.common.events.RideEvents.RideScheduled;
import com.uberclone.common.events.Topics;
import com.uberclone.matching.config.TemporalConfig;
import com.uberclone.matching.workflow.MatchingWorkflow;
import com.uberclone.matching.workflow.ScheduledRideWorkflow;
import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowOptions;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Kafka consumers that translate ride-service events into Temporal workflows.
 * This is what makes the system loosely coupled — ride-service publishes and
 * moves on; if matching-service is down, events queue up and are processed on
 * recovery.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RideEventListeners {

    private final WorkflowClient workflowClient;

    @KafkaListener(topics = Topics.RIDE_REQUESTED, groupId = "matching-service")
    public void onRideRequested(@Payload RideRequested evt,
                                @Header(KafkaHeaders.RECEIVED_KEY) String key) {
        log.info("← Kafka {} key={} rideId={}", Topics.RIDE_REQUESTED, key, evt.rideId());
        String workflowId = "ride-match-" + evt.rideId();
        MatchingWorkflow workflow = workflowClient.newWorkflowStub(
                MatchingWorkflow.class,
                WorkflowOptions.newBuilder()
                        .setTaskQueue(TemporalConfig.TASK_QUEUE)
                        .setWorkflowId(workflowId)
                        .setWorkflowExecutionTimeout(Duration.ofMinutes(5))
                        .build());
        try {
            WorkflowClient.start(workflow::matchRide, evt.rideId());
        } catch (io.temporal.client.WorkflowExecutionAlreadyStarted ignored) {
            log.info("Matching workflow already running for ride {}", evt.rideId());
        }
    }

    @KafkaListener(topics = Topics.RIDE_SCHEDULED, groupId = "matching-service")
    public void onRideScheduled(@Payload RideScheduled evt,
                                @Header(KafkaHeaders.RECEIVED_KEY) String key) {
        log.info("← Kafka {} key={} rideId={} fires at {}", Topics.RIDE_SCHEDULED, key, evt.rideId(), evt.scheduledFor());
        String workflowId = "ride-schedule-" + evt.rideId();
        ScheduledRideWorkflow workflow = workflowClient.newWorkflowStub(
                ScheduledRideWorkflow.class,
                WorkflowOptions.newBuilder()
                        .setTaskQueue(TemporalConfig.TASK_QUEUE)
                        .setWorkflowId(workflowId)
                        .setWorkflowExecutionTimeout(Duration.ofDays(31))
                        .build());
        try {
            WorkflowClient.start(workflow::waitAndFire, evt.rideId(), evt.scheduledFor());
        } catch (io.temporal.client.WorkflowExecutionAlreadyStarted ignored) {
            log.info("Schedule workflow already running for ride {}", evt.rideId());
        }
    }
}
