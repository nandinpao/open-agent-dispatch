package com.opensocket.aievent.core.operational;
import java.util.Set;import org.springframework.stereotype.Component;import com.opensocket.aievent.core.configuration.runtime.RuntimeConfigurationEffectiveValueResolver;
@Component public final class OperationalRuntimeConfigurationEffectiveValueResolver implements RuntimeConfigurationEffectiveValueResolver{
 private final OperationalRuntimeConfigurationView view; public OperationalRuntimeConfigurationEffectiveValueResolver(OperationalRuntimeConfigurationView view){this.view=view;}
 @Override public String owner(){return "OPENDISPATCH_OPERATIONAL";} @Override public Set<String> supportedKeys(){return OperationalRuntimeConfigurationView.ALL;}
 @Override public Object resolve(String key){return switch(key){case OperationalRuntimeConfigurationView.REFRESH_MS->view.refreshMs();case OperationalRuntimeConfigurationView.DISPATCH_SLO_SECONDS->view.dispatchSloSeconds();case OperationalRuntimeConfigurationView.RECONCILIATION_SLO_SECONDS->view.reconciliationSloSeconds();default->throw new IllegalArgumentException("OPENDISPATCH_OPERATIONAL_RUNTIME_CONFIG_KEY_NOT_OWNED key="+key);};}
}
