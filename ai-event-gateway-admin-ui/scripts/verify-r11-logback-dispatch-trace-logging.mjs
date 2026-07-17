import fs from 'node:fs';
import path from 'node:path';

const rootDir = fs.existsSync('ai-event-gateway-core') ? '.' : '..';
const resolve = (file) => path.join(rootDir, file);

const requiredFiles = [
  'ai-event-gateway-core/control-plane-app/src/main/resources/logback-spring.xml',
  'ai-event-gateway-netty/gateway-app/src/main/resources/logback-spring.xml',
  'docs/R11_LOGBACK_DISPATCH_TRACE_LOGGING/README.md',
  'docs/R11_LOGBACK_DISPATCH_TRACE_LOGGING/r11_change_log.md',
  'docs/R11_LOGBACK_DISPATCH_TRACE_LOGGING/r11_log_troubleshooting_runbook.md',
  'docs/R11_LOGBACK_DISPATCH_TRACE_LOGGING/r11_to_schema_cleanup_handoff.md',
  'ai-event-gateway-admin-ui/docs/R11_LOGBACK_DISPATCH_TRACE_LOGGING.md',
];

const requiredContent = [
  ['ai-event-gateway-core/control-plane-app/src/main/resources/logback-spring.xml', ['DISPATCH_TRACE_FILE', 'dispatch-trace.log', 'LOG_LEVEL_DISPATCH_TRACE', 'com.opensocket.aievent.core.routing.RoutingDecisionService']],
  ['ai-event-gateway-netty/gateway-app/src/main/resources/logback-spring.xml', ['GATEWAY_FILE', 'gateway.log', 'LOG_LEVEL_GATEWAY']],
  ['ai-event-gateway-core/control-plane-app/src/main/java/com/opensocket/aievent/core/api/EventIntakeController.java', ['LoggerFactory.getLogger', 'event_intake_received', 'event_intake_decided', 'MDC.put']],
  ['ai-event-gateway-core/event-processing/src/main/java/com/opensocket/aievent/core/processing/DefaultEventProcessingFacade.java', ['event_processing_started', 'event_processing_observed', 'event_processing_after_commit']],
  ['ai-event-gateway-core/task-orchestration/src/main/java/com/opensocket/aievent/core/dispatch/flow/FlowRuleRoutingService.java', ['flow_rule_plan_resolved', 'flow_rule_plan_not_matched', 'flow_rule_plan_applied']],
  ['ai-event-gateway-core/task-orchestration/src/main/java/com/opensocket/aievent/core/task/TaskDecisionService.java', ['task_create_attempt', 'task_flow_rule_gate_blocked', 'task_created']],
  ['ai-event-gateway-core/task-orchestration/src/main/java/com/opensocket/aievent/core/routing/RoutingDecisionService.java', ['routing_decision_started', 'routing_no_candidate', 'routing_below_minimum', 'routing_selected']],
  ['deploy/docker-compose.local.yml', ['opendispatch-core-logs:/logs', 'opendispatch-netty-logs:/logs', 'LOG_LEVEL_DISPATCH_TRACE']],
  ['deploy/docker-compose.ci.yml', ['opendispatch-ci-core-logs:/logs', 'opendispatch-ci-netty-logs:/logs', 'LOG_LEVEL_DISPATCH_TRACE']],
  ['deploy/env/.env.local.example', ['LOG_DIR=/logs', 'LOG_LEVEL_DISPATCH_TRACE=INFO']],
  ['docs/CURRENT_DISPATCH_DOMAIN_MODEL.md', ['R11 Logback / Dispatch Trace Logging', 'dispatch-trace.log']],
];

for (const file of requiredFiles) {
  if (!fs.existsSync(resolve(file))) {
    throw new Error(`Missing required R11 artifact: ${file}`);
  }
}

for (const [file, needles] of requiredContent) {
  if (!fs.existsSync(resolve(file))) {
    throw new Error(`Missing required R11 content file: ${file}`);
  }
  const text = fs.readFileSync(resolve(file), 'utf8');
  for (const needle of needles) {
    if (!text.includes(needle)) {
      throw new Error(`R11 verification failed: ${file} does not include ${needle}`);
    }
  }
}

console.log('OK R11 Logback / Dispatch Trace logging config, code log markers, compose mounts, docs, and verification gate are present.');
