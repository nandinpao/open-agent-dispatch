import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';

const root = path.resolve(import.meta.dirname, '..');
const read = (relative: string) => fs.readFileSync(path.join(root, relative), 'utf8');

test('C9 makes capability-first delegation the primary operations workspace', () => {
  const page = read('app/a2a-operations/page.tsx');
  const workspace = read('components/a2a-operations/CapabilityDelegationWorkspace.tsx');
  assert.match(page, /CapabilityDelegationWorkspace/);
  assert.match(page, /WHO CAN, WHO MAY, WHO SHOULD, HOW/);
  assert.match(workspace, /Capability-first operations/);
  assert.match(workspace, /Legacy A2A Archive/);
});

test('C9 primary detail renders Core decision authority chain instead of directional topology', () => {
  const detail = read('components/a2a-operations/CapabilityDelegationDetailView.tsx');
  assert.match(detail, /WHO CAN → WHO MAY → WHO SHOULD → HOW → Execution/);
  assert.match(detail, /Provider, binding, domain, pool, and transport are evidence outputs, never request authority/);
  assert.doesNotMatch(detail, /sourceDomainId.*targetDomainId/);
});

test('C9 legacy directional evidence is demoted and dual states are progressive disclosure', () => {
  const detail = read('components/a2a-operations/A2AOperationsDetailView.tsx');
  const workspace = read('components/a2a-operations/A2AOperationsWorkspace.tsx');
  assert.match(detail, /Legacy directional A2A evidence/);
  assert.doesNotMatch(detail, /Unknown source.*→.*Unknown provider domain/);
  assert.match(workspace, /Operational stage/);
  assert.match(workspace, /Advanced governance filter/);
  assert.match(workspace, /Governance request state/);
});

test('C9 legacy archive cannot advertise APPROVE into retired directional execution', () => {
  const actions = read('components/a2a-operations/GovernedActionsPanel.tsx');
  assert.doesNotMatch(actions, /case 'APPROVE'/);
  assert.doesNotMatch(actions, /\['APPROVE', 'REJECT'/);
  assert.match(actions, /Legacy directional requests cannot be approved into new execution/);
});

test('C9 capability decision projection is read-only and preserves provider-neutral runtime', () => {
  const controller = fs.readFileSync(path.resolve(root, '../ai-event-gateway-core/control-plane-app/src/main/java/com/opensocket/aievent/core/api/CapabilityDelegationOperationsController.java'), 'utf8');
  const runtime = fs.readFileSync(path.resolve(root, '../ai-event-gateway-core/control-plane-app/src/main/java/com/opensocket/aievent/core/capability/ManagedCapabilityDelegationRuntimeService.java'), 'utf8');
  assert.match(controller, /@GetMapping/);
  assert.doesNotMatch(controller, /@PostMapping|@PutMapping|@DeleteMapping/);
  assert.doesNotMatch(runtime, /targetAgentId|targetAgentPoolId|targetDomainId/);
});
