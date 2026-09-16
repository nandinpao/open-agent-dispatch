import assert from 'node:assert/strict';
import { describe, it } from 'node:test';
import { coreAdminApi } from '../lib/api/coreAdminApi';
import { coreAdminEndpoints } from '../lib/api/endpoints';

describe('Core Admin API contract guard', () => {
  it('keeps governance restore, credential issue, and enrollment approval on distinct Core routes', () => {
    assert.equal(coreAdminEndpoints.agentEnrollmentApprove('enroll-1'), '/admin/agent-enrollments/enroll-1/approve');
    assert.equal(coreAdminEndpoints.agentApprove('agent-1'), '/admin/agents/agent-1/approve');
    assert.equal(coreAdminEndpoints.agentCredentialIssue('agent-1'), '/admin/agents/agent-1/credentials/issue');
    assert.notEqual(coreAdminEndpoints.agentEnrollmentApprove('enroll-1'), coreAdminEndpoints.agentApprove('agent-1'));
    assert.notEqual(coreAdminEndpoints.agentApprove('agent-1'), coreAdminEndpoints.agentCredentialIssue('agent-1'));
  });

  it('exposes typed client methods for the P1 governance boundary', () => {
    assert.equal(typeof coreAdminApi.approveAgentEnrollment, 'function');
    assert.equal(typeof coreAdminApi.approveAgent, 'function');
    assert.equal(typeof coreAdminApi.issueAgentCredential, 'function');
    assert.equal(typeof coreAdminApi.updateAgentProfile, 'function');
  });

  it('keeps P2 runtime reconcile and skill drift policy on explicit operational routes', () => {
    assert.equal(coreAdminEndpoints.agentRuntimeDisconnectReconcile, '/admin/agents/runtime-disconnect/reconcile');
    assert.equal(coreAdminEndpoints.agentSkillsDriftPolicyEvaluate, '/admin/agent-skills/drift/policy-evaluate');
    assert.equal(typeof coreAdminApi.reconcileBlockedAgentRuntimes, 'function');
    assert.equal(typeof coreAdminApi.evaluateSkillDriftPolicy, 'function');
  });


  it('keeps TODO 15-E timeline and failure queue on explicit Core Admin routes', () => {
    assert.equal(coreAdminEndpoints.taskTimeline('task-1'), '/admin/tasks/task-1/timeline');
    assert.equal(coreAdminEndpoints.taskDispatchEvidence('task-1'), '/admin/tasks/task-1/dispatch-evidence');
    assert.equal(coreAdminEndpoints.taskRuntimeVerification('task-1'), '/admin/tasks/task-1/runtime-verification');
    assert.equal(coreAdminEndpoints.taskDispatchContractReadiness('task-1'), '/admin/tasks/task-1/dispatch-contract-readiness');
    assert.equal(coreAdminEndpoints.taskRepairDispatchContract('task-1'), '/admin/tasks/task-1/repair-dispatch-contract');
    assert.equal(coreAdminEndpoints.taskFailureQueue, '/admin/tasks/failure-queue');
    assert.equal(coreAdminEndpoints.taskManualRetry('task-1'), '/admin/tasks/task-1/manual-retry');
    assert.equal(coreAdminEndpoints.taskDeadLetter('task-1'), '/admin/tasks/task-1/dead-letter');
    assert.equal(coreAdminEndpoints.taskEscalate('task-1'), '/admin/tasks/task-1/escalate');
    assert.equal(typeof coreAdminApi.getTaskTimeline, 'function');
    assert.equal(typeof coreAdminApi.getTaskRuntimeVerification, 'function');
    assert.equal(typeof coreAdminApi.getTaskFailureQueue, 'function');
    assert.equal(typeof coreAdminApi.manualRetryTask, 'function');
    assert.equal(typeof coreAdminApi.deadLetterTask, 'function');
    assert.equal(typeof coreAdminApi.escalateTask, 'function');
  });


  it('exposes P4 routing explainability on the explicit Core Admin task route', () => {
    assert.equal(coreAdminEndpoints.taskRoutingDecisions('task-1'), '/admin/tasks/task-1/routing-decisions');
    assert.equal(typeof coreAdminApi.getTaskRoutingDecisions, 'function');
  });


  it('exposes P5 Agent remediation proposal on an explicit Core Admin route', () => {
    assert.equal(coreAdminEndpoints.agentRemediationProposal('agent-1'), '/admin/agents/agent-1/remediation/proposal');
    assert.equal(typeof coreAdminApi.getAgentRemediationProposal, 'function');
    assert.equal(typeof coreAdminApi.createAgentRemediationProposal, 'function');
    assert.equal(typeof coreAdminApi.clearRuntimeBackoff, 'function');
    assert.equal(typeof coreAdminApi.suspendAgent, 'function');
    assert.equal(typeof coreAdminApi.syncAgentApprovedSkillsAndCapabilities, 'function');
  });


  it('exposes P8 Agent remediation approval and execution workflow on explicit Core Admin routes', () => {
    assert.equal(coreAdminEndpoints.agentRemediationWorkflows('agent-1'), '/admin/agents/agent-1/remediation/workflows');
    assert.equal(coreAdminEndpoints.agentRemediationWorkflow('agent-1', 'wf-1'), '/admin/agents/agent-1/remediation/workflows/wf-1');
    assert.equal(coreAdminEndpoints.agentRemediationWorkflowApprove('agent-1', 'wf-1'), '/admin/agents/agent-1/remediation/workflows/wf-1/approve');
    assert.equal(coreAdminEndpoints.agentRemediationWorkflowReject('agent-1', 'wf-1'), '/admin/agents/agent-1/remediation/workflows/wf-1/reject');
    assert.equal(coreAdminEndpoints.agentRemediationWorkflowCancel('agent-1', 'wf-1'), '/admin/agents/agent-1/remediation/workflows/wf-1/cancel');
    assert.equal(coreAdminEndpoints.agentRemediationWorkflowExecute('agent-1', 'wf-1'), '/admin/agents/agent-1/remediation/workflows/wf-1/execute');
    assert.equal(typeof coreAdminApi.listAgentRemediationWorkflows, 'function');
    assert.equal(typeof coreAdminApi.createAgentRemediationWorkflow, 'function');
    assert.equal(typeof coreAdminApi.approveAgentRemediationWorkflow, 'function');
    assert.equal(typeof coreAdminApi.rejectAgentRemediationWorkflow, 'function');
    assert.equal(typeof coreAdminApi.cancelAgentRemediationWorkflow, 'function');
    assert.equal(typeof coreAdminApi.executeAgentRemediationWorkflow, 'function');
  });

  it('exposes P11 stale remediation workflow lease queue routes', () => {
    assert.equal(coreAdminEndpoints.agentRemediationWorkflowStaleLeases, '/admin/remediation/workflow-leases/stale');
    assert.equal(coreAdminEndpoints.agentRemediationWorkflowRecoveredLeases, '/admin/remediation/workflow-leases/recovered');
    assert.equal(coreAdminEndpoints.agentRemediationWorkflowRecoverStaleLeases, '/admin/remediation/workflow-leases/recover-stale');
    assert.equal(typeof coreAdminApi.listStaleAgentRemediationWorkflowLeases, 'function');
    assert.equal(typeof coreAdminApi.listRecoveredAgentRemediationWorkflowLeases, 'function');
    assert.equal(typeof coreAdminApi.recoverStaleAgentRemediationWorkflowLeases, 'function');
  });


  it('exposes first-agent setup as a single backend contract', () => {
    assert.equal(coreAdminEndpoints.agentSetup, '/admin/agents/setup');
    assert.equal(coreAdminEndpoints.agentSetupReadiness('agent-1'), '/admin/agents/agent-1/setup-readiness');
    assert.equal(coreAdminEndpoints.agentOperationalView('agent-1'), '/admin/agents/agent-1/operational-view');
    assert.equal(coreAdminEndpoints.agentLatestAuthFailure('agent-1'), '/admin/agents/agent-1/latest-auth-failure');
    assert.equal(coreAdminEndpoints.agentConnectionRepairActions('agent-1'), '/admin/agents/agent-1/connection-repair-actions');
    assert.equal(coreAdminEndpoints.agentConnectionRepairActionExecute('agent-1', 'ROTATE_CREDENTIAL'), '/admin/agents/agent-1/connection-repair-actions/ROTATE_CREDENTIAL');
    assert.equal(typeof coreAdminApi.setupAgent, 'function');
    assert.equal(typeof coreAdminApi.getAgentSetupReadiness, 'function');
    assert.equal(typeof coreAdminApi.getAgentOperationalView, 'function');
    assert.equal(typeof coreAdminApi.getAgentLatestAuthFailure, 'function');
    assert.equal(typeof coreAdminApi.getAgentConnectionRepairActions, 'function');
    assert.equal(typeof coreAdminApi.executeAgentConnectionRepairAction, 'function');
  });


  it('exposes Phase 6 source-system master-data routes', () => {
    assert.equal(coreAdminEndpoints.sourceSystems, '/admin/source-systems');
    assert.equal(coreAdminEndpoints.sourceSystem('SRC_E2E_7F28'), '/admin/source-systems/SRC_E2E_7F28');
    assert.equal(typeof coreAdminApi.getSourceSystems, 'function');
    assert.equal(typeof coreAdminApi.createSourceSystem, 'function');
    assert.equal(typeof coreAdminApi.updateSourceSystem, 'function');
    assert.equal(typeof coreAdminApi.retireSourceSystem, 'function');
  });


  it('exposes P2 capability-first dispatch contract builder routes', () => {
    assert.equal(coreAdminEndpoints.dispatchContractSourceSystems, '/admin/dispatch-contracts/source-systems');
    assert.equal(coreAdminEndpoints.dispatchContractBootstrap, '/admin/dispatch-contracts/bootstrap');
    assert.equal(coreAdminEndpoints.dispatchContractReadiness, '/admin/dispatch-contracts/readiness');
    assert.equal(coreAdminEndpoints.dispatchContractTestTask, '/admin/dispatch-contracts/test-task');
    assert.equal(typeof coreAdminApi.getDispatchContractSourceSystems, 'function');
    assert.equal(typeof coreAdminApi.bootstrapDispatchContract, 'function');
    assert.equal(typeof coreAdminApi.checkDispatchContractReadiness, 'function');
    assert.equal(typeof coreAdminApi.createDispatchContractTestTask, 'function');
  });

});
