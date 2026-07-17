package com.opensocket.aievent.gateway.netty.async;

import io.micrometer.context.ContextRegistry;
import io.micrometer.context.integration.Slf4jThreadLocalAccessor;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Registers bounded OpenDispatch MDC keys for Spring-managed task propagation. */
@Configuration(proxyBeanMethods = false)
public class OpenDispatchMdcContextPropagationConfiguration {

    private static final String[] PROPAGATED_MDC_KEYS = {
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
    MdcAccessorRegistration openDispatchMdcAccessorRegistration() {
        return new MdcAccessorRegistration(ContextRegistry.getInstance());
    }

    static final class MdcAccessorRegistration implements AutoCloseable {
        private final ContextRegistry registry;

        MdcAccessorRegistration(ContextRegistry registry) {
            this.registry = registry;
            this.registry.registerThreadLocalAccessor(new Slf4jThreadLocalAccessor(PROPAGATED_MDC_KEYS));
        }

        @Override
        public void close() {
            this.registry.removeThreadLocalAccessor(Slf4jThreadLocalAccessor.KEY);
        }
    }
}
