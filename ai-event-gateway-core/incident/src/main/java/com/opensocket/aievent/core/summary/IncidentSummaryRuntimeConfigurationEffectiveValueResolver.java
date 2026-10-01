package com.opensocket.aievent.core.summary;
import java.util.Set;import org.springframework.stereotype.Component;import com.opensocket.aievent.core.configuration.runtime.RuntimeConfigurationEffectiveValueResolver;
@Component public final class IncidentSummaryRuntimeConfigurationEffectiveValueResolver implements RuntimeConfigurationEffectiveValueResolver{
 private final IncidentSummaryRuntimeConfigurationView view; public IncidentSummaryRuntimeConfigurationEffectiveValueResolver(IncidentSummaryRuntimeConfigurationView view){this.view=view;}
 @Override public String owner(){return "INCIDENT_SUMMARY";} @Override public Set<String> supportedKeys(){return IncidentSummaryRuntimeConfigurationView.ALL;}
 @Override public Object resolve(String key){if(IncidentSummaryRuntimeConfigurationView.WINDOW.equals(key))return view.window().toString();throw new IllegalArgumentException("INCIDENT_SUMMARY_RUNTIME_CONFIG_KEY_NOT_OWNED key="+key);}
}
