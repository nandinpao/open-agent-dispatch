import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';
import { ROUTE_FAMILY_FEATURES, settingsFeatureForPath } from '../lib/navigation/routeFamilyEntitlements.ts';

const root = path.resolve(import.meta.dirname, '..');
const read = (relative: string) => fs.readFileSync(path.join(root, relative), 'utf8');

test('C10 centralizes route-family feature projections without inventing role authority', () => {
  assert.equal(ROUTE_FAMILY_FEATURES.agents, 'agents');
  assert.equal(ROUTE_FAMILY_FEATURES.delegations, 'a2a-operations');
  assert.equal(ROUTE_FAMILY_FEATURES.resourceAccess, 'resource-access');
  assert.equal(settingsFeatureForPath('/settings/integrations'), 'integrations');
  assert.equal(settingsFeatureForPath('/settings/capabilities'), 'administration');
  assert.equal(settingsFeatureForPath('/settings/runtime-resources'), 'administration');
});

test('C10 family layouts guard nested routes and avoid duplicate page wrappers', () => {
  for (const [family, key] of [['agents', 'agents'], ['a2a-operations', 'delegations'], ['resource-access', 'resourceAccess']] as const) {
    const layout = read(`app/${family}/layout.tsx`);
    assert.match(layout, /EntitlementPageGuard/);
    assert.match(layout, new RegExp(`ROUTE_FAMILY_FEATURES\\.${key}`));
  }
  const settingsLayout = read('app/settings/layout.tsx');
  assert.match(settingsLayout, /SettingsRouteEntitlementGuard/);
});

test('C10 settings IA separates dispatch, delegation governance, integrations, and runtime safety', () => {
  const settings = read('app/settings/page.tsx');
  assert.match(settings, /Dispatch Configuration/);
  assert.match(settings, /Delegation & Execution Governance/);
  assert.match(settings, /Integrations/);
  assert.match(settings, /Runtime & Safety/);
  assert.match(settings, /Advanced planning & learning/);
  assert.match(settings, /Release & engineering controls/);
});

test('C10 Agent runtime journey stays inside Agents instead of redirecting to Settings', () => {
  const runtime = read('app/agents/runtime/page.tsx');
  const detail = read('components/agents/AgentDetailProductView.tsx');
  const advanced = read('components/agents/AgentAdvancedWorkspace.tsx');
  assert.doesNotMatch(runtime, /redirect\(/);
  assert.match(runtime, /Agent Runtime Journey/);
  assert.match(runtime, /RuntimeResourceConsole/);
  assert.match(`${detail}\n${advanced}`, /managementHref\('\/agents\/runtime', agentId\)/);
});

test('C10 removes proven phase islands and obsolete Task navigation copy', () => {
  for (const phase of [3, 4, 5, 6, 7]) {
    assert.equal(fs.existsSync(path.join(root, `components/access-management/phase${phase}`)), false);
  }
  const messages = JSON.parse(read('messages/en-US.json')) as Record<string, string>;
  assert.equal(messages['task.sections.a2a'], 'Delegation');
  for (const key of ['task.sections.related', 'task.sections.issues', 'task.sections.sync', 'task.sections.audit']) {
    assert.equal(key in messages, false);
  }
});
