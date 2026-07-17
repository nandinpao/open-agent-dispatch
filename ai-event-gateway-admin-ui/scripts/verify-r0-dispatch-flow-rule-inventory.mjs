#!/usr/bin/env node
import fs from 'node:fs';
import path from 'node:path';
import process from 'node:process';

const repoRoot = path.resolve(process.cwd(), '..');
const docsDir = path.join(repoRoot, 'docs', 'R0_DISPATCH_FLOW_RULE_RESTRUCTURE');

const requiredFiles = [
  'README.md',
  'requirement_freeze.md',
  'legacy_feature_inventory.md',
  'routing_path_inventory.md',
  'ui_navigation_inventory.md',
  'schema_inventory.md',
  'migration_risk_report.md',
  'r0_to_r1_handoff.md',
  'legacy_feature_inventory.json',
  'routing_path_inventory.json',
  'ui_navigation_inventory.json',
];

const requiredTerms = new Map([
  ['requirement_freeze.md', ['eventStage', 'Flow -> Rule -> Skill -> Agent', 'Dispatch Rule', 'OpenClaw Skill']],
  ['legacy_feature_inventory.md', ['/dispatch-capabilities', '/dispatch-policies', 'CAPABILITY_FIRST', 'dispatch_event_task_mappings']],
  ['routing_path_inventory.md', ['CAPABILITY_FIRST', 'matchedFlowId', 'matchedRuleId', 'FLOW_RULE']],
  ['ui_navigation_inventory.md', ['Dispatch Flows', 'readonly', 'Flow-managed']],
  ['schema_inventory.md', ['dispatch_flows', 'dispatch_rules', 'flow_required_skills', 'tasks.matched_flow_id']],
  ['migration_risk_report.md', ['No destructive migration', 'shadow mode', 'legacy fallback']],
  ['r0_to_r1_handoff.md', ['R1 next work', 'Dispatch Flows', 'Do not change routing engine yet']],
]);

function fail(message) {
  console.error(`[R0 inventory verification] ${message}`);
  process.exitCode = 1;
}

if (!fs.existsSync(docsDir)) {
  fail(`Missing docs directory: ${path.relative(repoRoot, docsDir)}`);
  process.exit();
}

for (const fileName of requiredFiles) {
  const filePath = path.join(docsDir, fileName);
  if (!fs.existsSync(filePath)) {
    fail(`Missing required R0 artifact: ${path.relative(repoRoot, filePath)}`);
    continue;
  }

  const stat = fs.statSync(filePath);
  if (stat.size === 0) {
    fail(`R0 artifact is empty: ${path.relative(repoRoot, filePath)}`);
  }

  if (fileName.endsWith('.json')) {
    try {
      JSON.parse(fs.readFileSync(filePath, 'utf8'));
    } catch (error) {
      fail(`Invalid JSON in ${path.relative(repoRoot, filePath)}: ${error.message}`);
    }
  }
}

for (const [fileName, terms] of requiredTerms.entries()) {
  const filePath = path.join(docsDir, fileName);
  if (!fs.existsSync(filePath)) {
    continue;
  }
  const text = fs.readFileSync(filePath, 'utf8');
  for (const term of terms) {
    if (!text.includes(term)) {
      fail(`Expected term "${term}" not found in ${path.relative(repoRoot, filePath)}`);
    }
  }
}

if (process.exitCode) {
  process.exit();
}

console.log('[R0 inventory verification] OK');
console.log(`Verified ${requiredFiles.length} R0 artifacts in ${path.relative(repoRoot, docsDir)}.`);
