package com.opensocket.aievent.core.dedup;
import java.util.Set;
import org.springframework.stereotype.Component;
import com.opensocket.aievent.core.configuration.runtime.RuntimeConfigurationEffectiveValueResolver;
@Component
public final class EventDedupOperationalRuntimeConfigurationEffectiveValueResolver implements RuntimeConfigurationEffectiveValueResolver {
 private final EventDedupOperationalRuntimeConfigurationView view;
 public EventDedupOperationalRuntimeConfigurationEffectiveValueResolver(EventDedupOperationalRuntimeConfigurationView view){this.view=view;}
 @Override public String owner(){return "EVENT_DEDUP_OPERATIONAL";}
 @Override public Set<String> supportedKeys(){return EventDedupOperationalRuntimeConfigurationView.ALL;}
 @Override public Object resolve(String key){return switch(key){
  case EventDedupOperationalRuntimeConfigurationView.LOCK_WAIT_SECONDS -> view.lockWaitSeconds();
  case EventDedupOperationalRuntimeConfigurationView.LOCK_LEASE_SECONDS -> view.lockLeaseSeconds();
  default -> throw new IllegalArgumentException("EVENT_DEDUP_RUNTIME_CONFIG_KEY_NOT_OWNED key="+key);
 };}
}
