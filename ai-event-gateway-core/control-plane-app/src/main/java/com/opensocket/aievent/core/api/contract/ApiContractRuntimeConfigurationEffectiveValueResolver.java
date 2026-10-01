package com.opensocket.aievent.core.api.contract;

import java.util.Set;
import org.springframework.stereotype.Component;
import com.opensocket.aievent.core.configuration.runtime.RuntimeConfigurationEffectiveValueResolver;

@Component
public final class ApiContractRuntimeConfigurationEffectiveValueResolver implements RuntimeConfigurationEffectiveValueResolver {
    private final ApiContractRuntimeConfigurationView view;
    public ApiContractRuntimeConfigurationEffectiveValueResolver(ApiContractRuntimeConfigurationView view){this.view=view;}
    @Override public String owner(){return "CORE_API_CONTRACT";}
    @Override public Set<String> supportedKeys(){return ApiContractRuntimeConfigurationView.ALL;}
    @Override public Object resolve(String key){return switch(key){
        case ApiContractRuntimeConfigurationView.ENFORCEMENT -> view.enforcement().name();
        case ApiContractRuntimeConfigurationView.REQUIRE_IDEMPOTENCY -> view.requireIdempotency();
        case ApiContractRuntimeConfigurationView.REQUIRE_CORRELATION_ID -> view.requireCorrelationId();
        case ApiContractRuntimeConfigurationView.REQUIRE_EXPECTED_VERSION -> view.requireExpectedVersion();
        case ApiContractRuntimeConfigurationView.REQUIRE_ACTOR_IDENTITY -> view.requireActorIdentity();
        case ApiContractRuntimeConfigurationView.REQUIRE_AUDIT_REASON -> view.requireAuditReason();
        default -> throw new IllegalArgumentException("CORE_API_CONTRACT_RUNTIME_CONFIG_KEY_NOT_OWNED key="+key);
    };}
}
