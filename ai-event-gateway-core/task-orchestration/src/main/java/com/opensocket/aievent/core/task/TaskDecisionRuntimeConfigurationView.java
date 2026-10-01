package com.opensocket.aievent.core.task;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.opensocket.aievent.core.configuration.runtime.RuntimeConfigurationAuthorityRegistry;
import com.opensocket.aievent.core.configuration.runtime.RuntimeConfigurationSetKeys;
import com.opensocket.aievent.core.configuration.runtime.RuntimeConfigurationSnapshotValues;
import com.opensocket.aievent.core.event.EventSeverity;
import com.opensocket.aievent.core.routing.RoutingPolicy;

/** V41-C3R2M typed runtime view for active task decision policy. */
@Component
public final class TaskDecisionRuntimeConfigurationView {
    public static final String SET_KEY = RuntimeConfigurationSetKeys.CORE_SYSTEM;
    public static final String TASK_ESCALATION_ENABLED = "core.decision.task-escalation-enabled";
    public static final String TASK_MIN_OCCURRENCES = "core.decision.task-min-occurrences";
    public static final String IMMEDIATE_TASK_SEVERITIES = "core.decision.immediate-task-severities";
    public static final String DEFAULT_ROUTING_POLICY = "core.decision.default-routing-policy";
    public static final Set<String> ALL = Set.of(TASK_ESCALATION_ENABLED, TASK_MIN_OCCURRENCES, IMMEDIATE_TASK_SEVERITIES, DEFAULT_ROUTING_POLICY);

    private final RuntimeConfigurationSnapshotValues values;
    private final RuntimeConfigurationAuthorityRegistry authority;
    private final TaskOrchestrationProperties startup;

    @Autowired
    public TaskDecisionRuntimeConfigurationView(RuntimeConfigurationSnapshotValues values,
                                                RuntimeConfigurationAuthorityRegistry authority,
                                                TaskOrchestrationProperties startup) {
        this.values = values; this.authority = authority; this.startup = startup;
    }
    public TaskDecisionRuntimeConfigurationView(TaskOrchestrationProperties startup) {
        this(null, null, startup == null ? new TaskOrchestrationProperties() : startup);
    }

    /** Test/release authority intentionally remains startup-bound and outside runtime ALL. */
    public boolean taskCreationEnabled() { return startup.isTaskCreationEnabled(); }
    public boolean taskEscalationEnabled() { return booleanValue(TASK_ESCALATION_ENABLED, startup.isTaskEscalationEnabled()); }
    public int taskMinOccurrences() { int v=intValue(TASK_MIN_OCCURRENCES,startup.getTaskMinOccurrences()); if(v<1||v>1_000_000)throw invalid(TASK_MIN_OCCURRENCES); return v; }
    public Set<String> immediateTaskSeverities() {
        String fallback=String.join(",",startup.getImmediateTaskSeverities());
        String raw=textValue(IMMEDIATE_TASK_SEVERITIES,fallback);
        LinkedHashSet<String> out=new LinkedHashSet<>();
        Arrays.stream(raw.split(",")).map(String::trim).filter(v->!v.isBlank()).map(v->v.toUpperCase(Locale.ROOT)).forEach(out::add);
        if(out.isEmpty()) throw invalid(IMMEDIATE_TASK_SEVERITIES);
        for(String value:out){ try{ EventSeverity.valueOf(value); }catch(RuntimeException ex){ throw invalid(IMMEDIATE_TASK_SEVERITIES); } }
        return Set.copyOf(out);
    }
    public String defaultRoutingPolicy() {
        String v=textValue(DEFAULT_ROUTING_POLICY,startup.getDefaultRoutingPolicy());
        v=v==null?"":v.trim().toUpperCase(Locale.ROOT);
        if(v.isBlank()) throw invalid(DEFAULT_ROUTING_POLICY);
        try { RoutingPolicy.valueOf(v); } catch (RuntimeException ex) { throw invalid(DEFAULT_ROUTING_POLICY); }
        return v;
    }

    private boolean required(String key){return authority!=null&&authority.isRuntimeAuthoritative(key);} private void require(String key){if(values==null||!values.hasSnapshot(SET_KEY))throw incomplete(key);values.requireKeys(SET_KEY,ALL);}
    private boolean booleanValue(String key,boolean fallback){if(values==null)return fallback;if(required(key)){require(key);return values.booleanValue(SET_KEY,key).orElseThrow(()->incomplete(key));}return values.booleanValue(SET_KEY,key).orElse(fallback);}
    private int intValue(String key,int fallback){if(values==null)return fallback;if(required(key)){require(key);return values.integerValue(SET_KEY,key).orElseThrow(()->incomplete(key));}return values.integerValue(SET_KEY,key).orElse(fallback);}
    private String textValue(String key,String fallback){if(values==null)return fallback;if(required(key)){require(key);return values.textValue(SET_KEY,key).orElseThrow(()->incomplete(key));}return values.textValue(SET_KEY,key).orElse(fallback);}
    private static IllegalStateException invalid(String key){return new IllegalStateException("RUNTIME_CONFIG_VALIDATION_FAILED key="+key);} private static IllegalStateException incomplete(String key){return new IllegalStateException("CONFIGURATION_INCOMPLETE setKey="+SET_KEY+" key="+key);}
}
