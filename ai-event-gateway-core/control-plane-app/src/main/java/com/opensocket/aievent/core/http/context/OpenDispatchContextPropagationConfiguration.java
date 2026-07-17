package com.opensocket.aievent.core.http.context;

import io.micrometer.context.ContextRegistry;
import io.micrometer.context.integration.Slf4jThreadLocalAccessor;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Registers application-specific ThreadLocal state with Micrometer Context Propagation. */
@Configuration(proxyBeanMethods = false)
public class OpenDispatchContextPropagationConfiguration {

    static final String[] PROPAGATED_MDC_KEYS = {
            "requestId",
            "correlationId",
            "tenantId",
            "operatorId",
            "taskId",
            "agentId",
            "eventStage",
            "matchedFlowId",
            "matchedRuleId",
            "requestedSkill",
            "gatewayNodeId"
    };

    @Bean(destroyMethod = "close")
    OpenDispatchContextAccessorRegistration openDispatchContextAccessorRegistration() {
        return new OpenDispatchContextAccessorRegistration(ContextRegistry.getInstance());
    }

    static final class OpenDispatchContextAccessorRegistration implements AutoCloseable {
        private final ContextRegistry registry;

        OpenDispatchContextAccessorRegistration(ContextRegistry registry) {
            this.registry = registry;
            this.registry.registerThreadLocalAccessor(new OpenDispatchRequestContextThreadLocalAccessor());
            this.registry.registerThreadLocalAccessor(new Slf4jThreadLocalAccessor(PROPAGATED_MDC_KEYS));
        }

        @Override
        public void close() {
            this.registry.removeThreadLocalAccessor(OpenDispatchRequestContextThreadLocalAccessor.KEY);
            this.registry.removeThreadLocalAccessor(Slf4jThreadLocalAccessor.KEY);
        }
    }
}
