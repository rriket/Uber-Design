package com.uberclone.matching.config;

import com.uberclone.matching.workflow.MatchingActivitiesImpl;
import com.uberclone.matching.workflow.MatchingWorkflowImpl;
import com.uberclone.matching.workflow.SchedulingActivitiesImpl;
import com.uberclone.matching.workflow.ScheduledRideWorkflowImpl;
import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowClientOptions;
import io.temporal.serviceclient.WorkflowServiceStubs;
import io.temporal.serviceclient.WorkflowServiceStubsOptions;
import io.temporal.worker.Worker;
import io.temporal.worker.WorkerFactory;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.EventListener;

/**
 * Wires the Temporal Java SDK — worker factory, worker registered on the
 * matching task queue, and a WorkflowClient bean for controllers to submit
 * new workflow executions.
 */
@Slf4j
@Configuration
public class TemporalConfig {

    public static final String TASK_QUEUE = "UBER_MATCHING_TASK_QUEUE";

    @Value("${temporal.target:127.0.0.1:7233}") private String target;
    @Value("${temporal.namespace:default}")     private String namespace;

    @Bean
    public WorkflowServiceStubs workflowServiceStubs() {
        return WorkflowServiceStubs.newServiceStubs(
                WorkflowServiceStubsOptions.newBuilder().setTarget(target).build());
    }

    @Bean
    public WorkflowClient workflowClient(WorkflowServiceStubs stubs) {
        return WorkflowClient.newInstance(stubs,
                WorkflowClientOptions.newBuilder().setNamespace(namespace).build());
    }

    @Bean
    public WorkerFactory workerFactory(WorkflowClient client,
                                       MatchingActivitiesImpl matchingActivities,
                                       SchedulingActivitiesImpl schedulingActivities) {
        WorkerFactory factory = WorkerFactory.newInstance(client);
        Worker worker = factory.newWorker(TASK_QUEUE);
        worker.registerWorkflowImplementationTypes(
                MatchingWorkflowImpl.class,
                ScheduledRideWorkflowImpl.class);
        worker.registerActivitiesImplementations(matchingActivities, schedulingActivities);
        return factory;
    }

    /** Start the worker once the Spring context is fully initialised. */
    @EventListener(ApplicationReadyEvent.class)
    public void startWorker(ApplicationReadyEvent event) {
        try {
            WorkerFactory factory = event.getApplicationContext().getBean(WorkerFactory.class);
            factory.start();
            log.info("Temporal worker started on queue {} (namespace {}, target {})", TASK_QUEUE, namespace, target);
        } catch (Exception e) {
            log.error("Failed to start Temporal worker: {}. Workflows won't execute until Temporal is reachable.", e.getMessage());
        }
    }
}
