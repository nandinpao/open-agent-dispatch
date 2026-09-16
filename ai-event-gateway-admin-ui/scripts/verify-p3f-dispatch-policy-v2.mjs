#!/usr/bin/env node
import { existsSync, readFileSync } from 'node:fs';

const checks = [
  ['Migration creates dispatch_policies', '../ai-event-gateway-core/database-platform/src/main/resources/db/migration/V71__p3f_dispatch_policy_v2.sql', 'create table if not exists dispatch_policies'],
  ['Migration creates policy scopes', '../ai-event-gateway-core/database-platform/src/main/resources/db/migration/V71__p3f_dispatch_policy_v2.sql', 'create table if not exists dispatch_policy_scopes'],
  ['Migration creates required capability rules', '../ai-event-gateway-core/database-platform/src/main/resources/db/migration/V71__p3f_dispatch_policy_v2.sql', 'dispatch_policy_required_capabilities'],
  ['Migration creates runtime feature rules', '../ai-event-gateway-core/database-platform/src/main/resources/db/migration/V71__p3f_dispatch_policy_v2.sql', 'dispatch_policy_required_runtime_features'],
  ['Migration creates quality rules', '../ai-event-gateway-core/database-platform/src/main/resources/db/migration/V71__p3f_dispatch_policy_v2.sql', 'dispatch_policy_quality_rules'],
  ['Migration creates scoring rules', '../ai-event-gateway-core/database-platform/src/main/resources/db/migration/V71__p3f_dispatch_policy_v2.sql', 'dispatch_policy_scoring_rules'],
  ['Migration creates fallback rules', '../ai-event-gateway-core/database-platform/src/main/resources/db/migration/V71__p3f_dispatch_policy_v2.sql', 'dispatch_policy_fallback_rules'],
  ['Migration creates legacy report', '../ai-event-gateway-core/database-platform/src/main/resources/db/migration/V71__p3f_dispatch_policy_v2.sql', 'dispatch_policy_legacy_migration_report'],
  ['Domain model DispatchPolicy exists', '../ai-event-gateway-core/data-model/src/main/java/com/opensocket/aievent/core/agent/assignment/DispatchPolicy.java', 'class DispatchPolicy'],
  ['Repository exposes Dispatch Policy v2', '../ai-event-gateway-core/data-model/src/main/java/com/opensocket/aievent/core/agent/assignment/AgentAssignmentRepository.java', 'searchDispatchPolicies'],
  ['Admin API exposes /admin/dispatch-policies', '../ai-event-gateway-core/control-plane-app/src/main/java/com/opensocket/aievent/core/api/AgentAssignmentController.java', '/dispatch-policies'],
  ['Admin UI type CoreDispatchPolicy exists', 'lib/types/core.ts', 'interface CoreDispatchPolicy'],
  ['Admin API client reads Dispatch Policy v2', 'lib/api/coreAdminApi.ts', 'getDispatchPoliciesV2'],
  ['Dispatch Policy v2 console exists', 'components/dispatch-policies/DispatchPolicyV2Console.tsx', 'P3-F Dispatch Policy v2'],
  ['Dispatch Policies page prioritizes v2 console', 'app/dispatch-policies/page.tsx', 'DispatchPolicyV2Console'],
  ['P3-F documentation exists', 'docs/P3_F_DISPATCH_POLICY_V2.md', 'Dispatch Policy v2'],
];

let failed = false;
for (const [label, path, token] of checks) {
  if (!existsSync(path)) {
    console.error(`[FAIL] ${label}: missing ${path}`);
    failed = true;
    continue;
  }
  const text = readFileSync(path, 'utf8');
  if (!text.includes(token)) {
    console.error(`[FAIL] ${label}: token not found: ${token}`);
    failed = true;
    continue;
  }
  console.log(`[OK] ${label}`);
}

if (failed) process.exit(1);
console.log('[OK] P3-F Dispatch Policy v2 verification passed.');
