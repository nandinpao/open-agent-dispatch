package com.opensocket.aievent.core.action;

import java.util.Set;
import org.springframework.stereotype.Component;
import com.opensocket.aievent.core.configuration.runtime.RuntimeConfigurationEffectiveValueResolver;

@Component
public final class AdapterActionPolicyRuntimeConfigurationEffectiveValueResolver implements RuntimeConfigurationEffectiveValueResolver {
    private static final Set<String> KEYS=AdapterActionPolicyRuntimeConfigurationView.ALL;
    private final AdapterActionPolicyRuntimeConfigurationView view;
    public AdapterActionPolicyRuntimeConfigurationEffectiveValueResolver(AdapterActionPolicyRuntimeConfigurationView view){this.view=view;}
    @Override public String owner(){return "ADAPTER_ACTION_POLICY";}
    @Override public Set<String> supportedKeys(){return KEYS;}
    @Override public Object resolve(String key){return switch(key){
        case AdapterActionPolicyRuntimeConfigurationView.CREATE_SUPPRESSED_RECORDS -> view.createSuppressedRecords();
        case AdapterActionPolicyRuntimeConfigurationView.ISSUE_ADAPTER_NAME -> view.issueAdapterName();
        default -> throw new IllegalArgumentException("ADAPTER_ACTION_POLICY_RUNTIME_CONFIG_KEY_NOT_OWNED key="+key);
    };}
}
