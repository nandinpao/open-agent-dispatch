package com.opensocket.aievent.core.dispatch.flow;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/**
 * P6.14 diagnostic marker. Emits the effective dispatch logging profile at startup
 * so uploaded log bundles can prove which runtime image / log-level profile was actually running.
 */
@Component
public class DispatchDiagnosticsStartupLogger {
    private static final Logger log = LoggerFactory.getLogger(DispatchDiagnosticsStartupLogger.class);
    private final Environment environment;

    public DispatchDiagnosticsStartupLogger(Environment environment) {
        this.environment = environment;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void logDispatchDiagnosticsProfile() {
        log.info("dispatch_diagnostics_logging_ready phase=P6_15 logDir={} rootLevel={} coreLevel={} dispatchTraceLevel={} tracingLevel={} profiles={} markers=flow_rule_db_lookup_started,flow_rule_runtime_repository_lookup_started,dispatch_request_created,dispatch_eligibility_backend_profile_skipped_flow_rule,dispatch_executor_batch_executed,gateway_dispatch_http_started,task_dispatch_recovery_claimed,task_dispatch_recovery_recovered,agent_task_dispatch_received,agent_task_result_callback_started,netty_callback_relay_received,callback_inbox_accepted,issue_sync_evaluation_started,agent_pending_callback_retained,agent_pending_callback_replayed,task_lifecycle_scan_started,task_lifecycle_auto_reassign_due,task_lifecycle_auto_assign_retry_due,callback_replay_duplicate_accepted,netty_callback_relay_sync_started,netty_callback_relay_sync_response,task_runtime_view_loaded,task_runtime_view_needs_action_candidate,task_failure_queue_loaded,task_failure_queue_item,gateway_diagnostics_logging_ready,task_runtime_view_terminal_historical_dispatch,generic_dispatch_authoritative_no_selection,generic_dispatch_authoritative_completed,agent_assignment_eligibility_blocked,agent_assignment_eligibility_pass,agent_readiness_blocked,agent_readiness_pass,agent_operational_readiness_blocked,agent_operational_readiness_pass,agent_setup_readiness_unavailable,agent_dispatch_eligibility_unavailable",
                property("LOG_DIR", "logs"),
                property("LOG_LEVEL_ROOT", "INFO"),
                property("LOG_LEVEL_CORE", "INFO"),
                property("LOG_LEVEL_DISPATCH_TRACE", "INFO"),
                property("LOG_LEVEL_TRACING", "INFO"),
                String.join(",", environment.getActiveProfiles()));
    }

    private String property(String name, String fallback) {
        String value = environment.getProperty(name);
        if (value == null || value.isBlank()) {
            value = System.getenv(name);
        }
        return value == null || value.isBlank() ? fallback : value;
    }
}
