import assert from 'node:assert/strict';
import { describe, it } from 'node:test';
import { coreAdminApi } from '../lib/api/coreAdminApi';
import { coreAdminEndpoints } from '../lib/api/endpoints';
import { adminInformationLayers } from '../lib/navigation/adminInformationArchitecture';

const capabilityFlowRoute = '/dispatch-flows';
const legacyBuilderRoute = '/settings/dispatch-contract-builder';

describe('P7/R1 Dispatch Flow navigation guard', () => {
  it('exposes one primary operator entry and keeps raw rule catalogs out of Basic workflow', () => {
    const operations = adminInformationLayers.find((layer) => layer.id === 'operations');
    const advanced = adminInformationLayers.find((layer) => layer.id === 'advanced');

    assert.ok(operations, 'Operations Hub information layer must exist');
    assert.equal(advanced, undefined, 'Current information architecture must not expose a parallel Advanced legacy setup layer');
    assert.ok(operations.primaryLinks.some((link) => link.href === capabilityFlowRoute), 'Operations Hub must expose Dispatch Flows as the single creation flow');
    assert.equal(operations.primaryLinks.some((link) => link.href === '/dispatch-contracts'), false, 'Operations Hub must not expose the compatibility Dispatch Contracts route');
    assert.equal(operations.primaryLinks.some((link) => link.href === '/dispatch-capabilities'), false, 'Operations Hub must not expose the compatibility Dispatch Capabilities route');
    assert.equal(operations.primaryLinks.some((link) => link.href === '/dispatch-policies'), false, 'Operations Hub must not expose raw Dispatch Rules as a separate Basic workflow');
    assert.equal(operations.primaryLinks.some((link) => link.href === legacyBuilderRoute), false, 'Operations Hub must not expose Legacy Contract Builder');
    assert.equal(adminInformationLayers.some((layer) => layer.primaryLinks.some((link) => link.href === '/dispatch-policies')), false, 'Raw Dispatch Rules must stay outside Current information layers');
    assert.equal(adminInformationLayers.some((layer) => layer.primaryLinks.some((link) => link.href === '/supply-profiles')), false, 'Legacy Supply Profiles must stay outside Current information layers');
  });

  it('keeps P2-M/P2-N/P2-O/P2-P Core Admin endpoints available to the UI', () => {
    assert.equal(coreAdminEndpoints.dispatchTaskDefinitions, '/admin/dispatch-task-definitions');
    assert.equal(coreAdminEndpoints.dispatchTaskDefinition('SOURCE_TASK_DEFINITION'), '/admin/dispatch-task-definitions/SOURCE_TASK_DEFINITION');
    assert.equal(coreAdminEndpoints.assignmentProfilePolicy('PROFILE_CODE', 'POLICY_CODE'), '/admin/assignment-profiles/PROFILE_CODE/policies/POLICY_CODE');
    assert.equal(coreAdminEndpoints.assignmentProfileCapability('PROFILE_CODE', 'CAPABILITY_CODE'), '/admin/assignment-profiles/PROFILE_CODE/capabilities/CAPABILITY_CODE');
    assert.equal(coreAdminEndpoints.taskEligibleAgents('task-1'), '/admin/tasks/task-1/eligible-agents');

    assert.equal(typeof coreAdminApi.getDispatchTaskDefinitions, 'function');
    assert.equal(typeof coreAdminApi.upsertDispatchTaskDefinition, 'function');
    assert.equal(typeof coreAdminApi.upsertAssignmentProfilePolicy, 'function');
    assert.equal(typeof coreAdminApi.upsertAssignmentProfileCapability, 'function');
    assert.equal(typeof coreAdminApi.getTaskEligibleAgents, 'function');
  });
});
