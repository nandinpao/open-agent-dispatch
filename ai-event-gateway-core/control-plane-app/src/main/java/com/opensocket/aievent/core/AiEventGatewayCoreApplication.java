package com.opensocket.aievent.core;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

import com.opensocket.aievent.core.config.CoreDecisionProperties;
import com.opensocket.aievent.core.config.CoreDeploymentProperties;
import com.opensocket.aievent.core.callback.TaskCallbackProperties;
import com.opensocket.aievent.core.action.AdapterActionProperties;
import com.opensocket.aievent.core.action.executor.AdapterActionExecutionProperties;
import com.opensocket.aievent.core.dedup.EventDedupRedisProperties;
import com.opensocket.aievent.core.routing.RoutingProperties;
import com.opensocket.aievent.core.dispatch.DispatchProperties;
import com.opensocket.aievent.core.summary.IncidentSummaryProperties;
import com.opensocket.aievent.core.fingerprint.FingerprintPolicyProperties;
import com.opensocket.aievent.core.lifecycle.LifecycleProperties;
import com.opensocket.aievent.core.observability.ObservabilityProperties;
import com.opensocket.aievent.core.processing.EventProcessingProperties;
import com.opensocket.aievent.core.incident.IncidentModuleProperties;
import com.opensocket.aievent.core.task.TaskOrchestrationProperties;
import com.opensocket.aievent.core.task.TaskDispatchRecoveryProperties;
import com.opensocket.aievent.core.outbox.OutboxProperties;
import com.opensocket.aievent.core.integration.IntegrationEventProperties;
import com.opensocket.aievent.core.security.CoreInternalSecurityProperties;
import com.opensocket.aievent.core.identity.AdminIdentityProperties;
import com.opensocket.aievent.core.runtime.RuntimeDisconnectProperties;
import com.opensocket.aievent.core.config.RecoveryGovernanceProperties;

@SpringBootApplication(excludeName = {
    "org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration",
    "org.springframework.boot.data.jpa.autoconfigure.DataJpaRepositoriesAutoConfiguration"
})
@EnableAsync
@EnableScheduling
@EnableConfigurationProperties({CoreDecisionProperties.class, CoreDeploymentProperties.class, EventDedupRedisProperties.class, IncidentSummaryProperties.class, RoutingProperties.class, DispatchProperties.class, TaskCallbackProperties.class, AdapterActionProperties.class, AdapterActionExecutionProperties.class, FingerprintPolicyProperties.class, LifecycleProperties.class, ObservabilityProperties.class, EventProcessingProperties.class, IncidentModuleProperties.class, TaskOrchestrationProperties.class, TaskDispatchRecoveryProperties.class, OutboxProperties.class, IntegrationEventProperties.class, CoreInternalSecurityProperties.class, AdminIdentityProperties.class, RuntimeDisconnectProperties.class, RecoveryGovernanceProperties.class})
public class AiEventGatewayCoreApplication {
    public static void main(String[] args) {
        SpringApplication.run(AiEventGatewayCoreApplication.class, args);
    }
}
