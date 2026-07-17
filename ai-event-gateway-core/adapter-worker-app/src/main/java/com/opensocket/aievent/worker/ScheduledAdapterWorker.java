package com.opensocket.aievent.worker;

import com.opensocket.aievent.service.adapter.AdapterWorkItem;
import io.micrometer.observation.ObservationRegistry;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class ScheduledAdapterWorker {
    private final AdapterWorkerProperties properties;
    private final CoreAdapterActionClient client;
    private final List<AdapterWorkExecutor> executors;
    private final TaskScheduler taskScheduler;
    private final ObservationRegistry observationRegistry;

    public ScheduledAdapterWorker(
            AdapterWorkerProperties properties,
            CoreAdapterActionClient client,
            List<AdapterWorkExecutor> executors,
            TaskScheduler taskScheduler,
            ObservationRegistry observationRegistry
    ) {
        this.properties = properties;
        this.client = client;
        this.executors = executors;
        this.taskScheduler = taskScheduler;
        this.observationRegistry = observationRegistry;
    }

    @Scheduled(fixedDelayString = "${adapter-worker.poll-interval-ms:2000}")
    public void poll() {
        if (!properties.isEnabled()) {
            return;
        }
        for (String type : properties.getAdapterTypes()) {
            if (properties.isConfigured(type)) {
                client.claim(type).ifPresent(this::execute);
            }
        }
    }

    private void execute(AdapterWorkItem item) {
        try (LeaseHeartbeatGuard ignored = new LeaseHeartbeatGuard(
                taskScheduler, observationRegistry, client, item, properties.getLeaseSeconds())) {
            AdapterWorkResult result = executors.stream()
                    .filter(executor -> executor.supports(item))
                    .findFirst()
                    .map(executor -> executor.execute(item))
                    .orElseGet(() -> AdapterWorkResult.failure("No executor for " + item.adapterType(), false));
            if (result.success()) {
                client.complete(item, result);
            }
            else {
                client.fail(item, result);
            }
        }
    }
}
