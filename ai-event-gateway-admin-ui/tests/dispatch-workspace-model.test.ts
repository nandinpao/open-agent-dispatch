import test from 'node:test';
import assert from 'node:assert/strict';

import {
  flowActivationIssues,
  flowHealthIssues,
  flowRuntimeReadinessIssues,
  flowSimulationIssues,
} from '../components/dispatch-workspace/dispatchWorkspaceModel';
import type { CoreAgentPoolView, CoreDispatchFlowView } from '../lib/types/core';

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
  rules: [],
};

test('dispatch workspace separates configuration/simulation from production activation lifecycle', () => {
  assert.deepEqual(flowHealthIssues(draftFlow, [pool]), []);
  assert.deepEqual(flowSimulationIssues(draftFlow, [pool]), []);
  assert.deepEqual(flowActivationIssues(draftFlow), ['Source Flow is not enabled. Set the Flow status to ACTIVE or ENABLED before runtime dispatch.']);
  assert.deepEqual(flowRuntimeReadinessIssues(draftFlow, [pool]), ['Source Flow is not enabled. Set the Flow status to ACTIVE or ENABLED before runtime dispatch.']);
});

test('compatibility lifecycle helper stops blocking after activation but does not claim Agent runtime eligibility', () => {
  assert.deepEqual(flowRuntimeReadinessIssues({ ...draftFlow, status: 'ACTIVE' }, [pool]), []);
});
