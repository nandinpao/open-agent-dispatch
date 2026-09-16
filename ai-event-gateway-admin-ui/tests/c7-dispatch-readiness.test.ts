import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';

import {
  flowActivationReadinessIssues,
  flowHealthIssues,
  flowSimulationIssues,
  simulationMatchesFlowVersion,
  simulationConfigurationPassed,
  simulationPassed,
} from '../components/dispatch-workspace/dispatchWorkspaceModel.ts';
import type { CoreAgentPoolView, CoreDispatchFlowView, CoreDispatchSimulationResponse } from '../lib/types/core.ts';

const pool: CoreAgentPoolView = {
  tenantId: 'tenant-a',
  poolId: 'pool-1',
  poolCode: 'ERP_POOL',
  poolName: 'ERP Pool',
  sourceSystem: 'ERP',
  status: 'ACTIVE',
};

const draftFlow: CoreDispatchFlowView = {
  tenantId: 'tenant-a',
  flowId: 'flow-1',
  flowCode: 'ERP_DEFAULT_FLOW',
  flowName: 'ERP Default Flow',
  sourceSystem: 'ERP',
  status: 'DRAFT',
  defaultPoolId: 'pool-1',
  version: 7,
  rules: [],
};

const successfulDraftSimulation: CoreDispatchSimulationResponse = {
  matchedFlowId: 'flow-1',
  flowVersion: '7',
  evaluationMode: 'DRAFT_SIMULATION',
  dispatchable: true,
  status: 'READY',
  sideEffectFree: true,
};

test('C7 allows a configured Draft Flow to run side-effect-free simulation before activation', () => {
  assert.deepEqual(flowHealthIssues(draftFlow, [pool]), []);
  assert.deepEqual(flowSimulationIssues(draftFlow, [pool]), []);
});

test('C7 activation evidence is bound to the current persisted Flow version', () => {
  assert.equal(simulationMatchesFlowVersion(draftFlow, successfulDraftSimulation), true);
  assert.equal(simulationPassed(successfulDraftSimulation), true);
  assert.deepEqual(flowActivationReadinessIssues(draftFlow, [pool], successfulDraftSimulation), []);

  const changedFlow = { ...draftFlow, version: 8 };
  assert.equal(simulationMatchesFlowVersion(changedFlow, successfulDraftSimulation), false);
  assert.match(flowActivationReadinessIssues(changedFlow, [pool], successfulDraftSimulation).join(' '), /changed after the last Draft Simulation/i);
});

test('C7 treats MANUAL_ONLY as a valid simulation outcome without pretending it is automatic dispatch', () => {
  const manual: CoreDispatchSimulationResponse = {
    ...successfulDraftSimulation,
    dispatchable: false,
    manualOnly: true,
    status: 'MANUAL_ASSIGNMENT_REQUIRED',
    blockerCode: 'MANUAL_ASSIGNMENT_REQUIRED',
  };
  assert.equal(simulationPassed(manual), true);
  assert.deepEqual(flowActivationReadinessIssues(draftFlow, [pool], manual), []);
});



test('C7 activation accepts durable Pool configuration when Agent runtime is not connected yet', () => {
  const runtimeMissing: CoreDispatchSimulationResponse = {
    ...successfulDraftSimulation,
    dispatchable: false,
    status: 'BLOCKED',
    targetPoolId: 'pool-1',
    poolMemberCount: 1,
    blockerCode: 'POOL_AGENT_RUNTIME_NOT_FOUND',
    blockerReason: 'No eligible Agent was available in the resolved Agent Pool.',
  };
  assert.equal(simulationPassed(runtimeMissing), false);
  assert.equal(simulationConfigurationPassed(runtimeMissing), true);
  assert.deepEqual(flowActivationReadinessIssues(draftFlow, [pool], runtimeMissing), []);
});

test('C7 activation still fails closed for a Pool with no active member', () => {
  const noMembers: CoreDispatchSimulationResponse = {
    ...successfulDraftSimulation,
    dispatchable: false,
    status: 'BLOCKED',
    targetPoolId: 'pool-1',
    poolMemberCount: 0,
    blockerCode: 'POOL_HAS_NO_ACTIVE_MEMBER',
  };
  assert.equal(simulationConfigurationPassed(noMembers), false);
  assert.match(flowActivationReadinessIssues(draftFlow, [pool], noMembers).join(' '), /POOL_HAS_NO_ACTIVE_MEMBER/);
});

test('C7 workspace keeps activation out of Step 1 and exposes the four ordered operational actions', () => {
  const root = path.resolve(import.meta.dirname, '..');
  const source = fs.readFileSync(path.join(root, 'components/dispatch-workspace/DispatchWorkspaceSections.tsx'), 'utf8');
  const flowBasics = source.slice(source.indexOf('title="Source Flow"'), source.indexOf('title="Workload Classification"'));
  assert.doesNotMatch(flowBasics, /Activate Flow/);
  assert.match(source, /1\. Run Safe Preview/);
  assert.match(source, /2\. Activate Flow/);
  assert.match(source, /3\. Check Live Readiness/);
  assert.match(source, /4\. Send Live Test/);
  assert.match(source, /evaluationMode: 'DRAFT_SIMULATION'/);
  assert.match(source, /evaluationMode: 'RUNTIME_READINESS'/);
});
