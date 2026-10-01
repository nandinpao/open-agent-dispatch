package com.opensocket.aievent.core.task;

import java.util.Set;
import org.springframework.stereotype.Component;
import com.opensocket.aievent.core.configuration.runtime.RuntimeConfigurationEffectiveValueResolver;

@Component
public final class TaskDecisionRuntimeConfigurationEffectiveValueResolver implements RuntimeConfigurationEffectiveValueResolver {
    private final TaskDecisionRuntimeConfigurationView view;
    public TaskDecisionRuntimeConfigurationEffectiveValueResolver(TaskDecisionRuntimeConfigurationView view){this.view=view;}
    @Override public String owner(){return "CORE_TASK_DECISION";}
    @Override public Set<String> supportedKeys(){return TaskDecisionRuntimeConfigurationView.ALL;}
    @Override public Object resolve(String key){return switch(key){
        case TaskDecisionRuntimeConfigurationView.TASK_ESCALATION_ENABLED -> view.taskEscalationEnabled();
        case TaskDecisionRuntimeConfigurationView.TASK_MIN_OCCURRENCES -> view.taskMinOccurrences();
        case TaskDecisionRuntimeConfigurationView.IMMEDIATE_TASK_SEVERITIES -> String.join(",",view.immediateTaskSeverities());
        case TaskDecisionRuntimeConfigurationView.DEFAULT_ROUTING_POLICY -> view.defaultRoutingPolicy();
        default -> throw new IllegalArgumentException("CORE_TASK_DECISION_RUNTIME_CONFIG_KEY_NOT_OWNED key="+key);
    };}
}
