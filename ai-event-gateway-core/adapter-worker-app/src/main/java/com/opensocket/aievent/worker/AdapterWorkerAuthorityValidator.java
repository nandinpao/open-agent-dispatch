package com.opensocket.aievent.worker;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/** Prevents the generic external worker from becoming a second Issue Tracking authority. */
@Component
public class AdapterWorkerAuthorityValidator implements ApplicationRunner {
    private final AdapterWorkerProperties properties;

    public AdapterWorkerAuthorityValidator(AdapterWorkerProperties properties) {
        this.properties = properties;
    }

    @Override
    public void run(ApplicationArguments args) {
        boolean requestsIssueTracking = properties.getAdapterTypes().stream()
                .anyMatch("ISSUE_TRACKING"::equalsIgnoreCase);
        if (requestsIssueTracking || !properties.getIssueEndpointUrl().isBlank()) {
            throw new IllegalStateException(
                    "ISSUE_TRACKING_EXTERNAL_WORKER_FORBIDDEN_CORE_GOVERNED_AUTHORITY: "
                            + "remove ISSUE_TRACKING from ADAPTER_WORKER_TYPES and remove ADAPTER_WORKER_ISSUE_ENDPOINT_URL");
        }
    }
}
