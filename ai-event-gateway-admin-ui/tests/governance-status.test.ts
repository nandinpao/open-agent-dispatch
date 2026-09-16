import assert from 'node:assert/strict';
import { describe, it } from 'node:test';
import { canApproveCoreAgentProfile, canIssueCredentialForRow, canRevokeCoreAgentProfile, deriveAgentGovernanceState, isCorrectableEnrollmentStatus, isRejectedEnrollmentStatus, normalizeEnrollmentStatus } from '../lib/agents/governanceStatus';
import { getRuntimeLatencyLabel } from '../lib/agents/agentRuntimeDisplay';
import type { AgentDashboardRow } from '../lib/types/dashboard';

function row(overrides: Partial<AgentDashboardRow>): AgentDashboardRow {
  return {
    agentId: 'agent-001',
    connected: false,
    source: { profile: 'CORE', runtime: 'NETTY' },
    ...overrides
  };
}

describe('P1 Agent Governance status semantics', () => {
  it('marks an approved/enabled/normal/active-credential/authorized runtime as READY', () => {
    const state = deriveAgentGovernanceState(row({
      profile: {
        agentId: 'agent-001',
        approvalStatus: 'APPROVED',
        enabled: true,
        riskStatus: 'NORMAL',
        credential: { credentialId: 'cred-1', credentialStatus: 'ACTIVE' }
      },
      runtime: {
        agentId: 'agent-001',
        connected: true,
        authorizationState: 'AUTHORIZED'
      }
    }));

    assert.equal(state.readinessStatus, 'READY');
    assert.equal(state.isReady, true);
    assert.equal(state.isCoreAssignableCandidate, true);
  });

  it('blocks READY when credential material is missing even if profile is approved and runtime is authorized', () => {
    const state = deriveAgentGovernanceState(row({
      profile: {
        agentId: 'agent-001',
        approvalStatus: 'APPROVED',
        enabled: true,
        riskStatus: 'NORMAL'
      },
      runtime: {
        agentId: 'agent-001',
        connected: true,
        authorizationState: 'AUTHORIZED'
      }
    }));

    assert.equal(state.credentialStatus, 'CREDENTIAL_MISSING');
    assert.equal(state.readinessStatus, 'CREDENTIAL_MISSING');
    assert.equal(state.isReady, false);
  });

  it('treats missing runtime authorizationState as AUTH_UNKNOWN, not authorized', () => {
    const state = deriveAgentGovernanceState(row({
      profile: {
        agentId: 'agent-001',
        approvalStatus: 'APPROVED',
        enabled: true,
        riskStatus: 'NORMAL',
        credential: { credentialId: 'cred-1', credentialStatus: 'ACTIVE' }
      },
      runtime: {
        agentId: 'agent-001',
        connected: true
      }
    }));

    assert.equal(state.runtimeAuthorizationStatus, 'AUTH_UNKNOWN');
    assert.equal(state.readinessStatus, 'AUTH_UNKNOWN');
    assert.equal(state.isReady, false);
  });

  it('marks Netty-only rows as ungoverned runtime observations', () => {
    const state = deriveAgentGovernanceState(row({
      profile: undefined,
      source: { profile: 'MISSING', runtime: 'NETTY' },
      runtime: {
        agentId: 'agent-001',
        connected: true,
        authorizationState: 'AUTHORIZED'
      }
    }));

    assert.equal(state.sourceStatus, 'RUNTIME_ONLY_UNGOVERNED');
    assert.equal(state.profileStatus, 'NO_CORE_PROFILE');
    assert.equal(state.readinessStatus, 'NO_CORE_PROFILE');
    assert.equal(state.isRuntimeOnlyUngoverned, true);
  });

  it('keeps rejected enrollment separate from rejected Core profile', () => {
    const state = deriveAgentGovernanceState(row({
      profile: {
        agentId: 'agent-001',
        approvalStatus: 'REJECTED',
        enabled: false,
        riskStatus: 'NORMAL'
      },
      enrollment: {
        enrollmentId: 'enroll-001',
        claimedAgentId: 'agent-001',
        status: 'REJECTED'
      }
    }));

    assert.equal(state.profileStatus, 'PROFILE_REJECTED');
    assert.equal(state.enrollmentStatus, 'ENROLLMENT_REJECTED');
    assert.equal(state.readinessStatus, 'PROFILE_NOT_APPROVED');
  });


  it('keeps rejected enrollments correctable for manual review mistakes', () => {
    const state = deriveAgentGovernanceState(row({
      profile: undefined,
      enrollment: {
        enrollmentId: 'enroll-rejected-001',
        claimedAgentId: 'agent-001',
        status: 'REJECTED'
      }
    }));

    assert.equal(state.enrollmentStatus, 'ENROLLMENT_REJECTED');
    assert.equal(state.requiresReview, true);
  });


  it('normalizes rejected enrollment statuses so Approve Again remains available', () => {
    assert.equal(normalizeEnrollmentStatus('rejected'), 'REJECTED');
    assert.equal(normalizeEnrollmentStatus('ENROLLMENT_REJECTED'), 'REJECTED');
    assert.equal(isRejectedEnrollmentStatus('rejected'), true);
    assert.equal(isRejectedEnrollmentStatus('ENROLLMENT_REJECTED'), true);
    assert.equal(isCorrectableEnrollmentStatus('rejected'), true);
    assert.equal(isCorrectableEnrollmentStatus('ENROLLMENT_REJECTED'), true);
  });


  it('treats rejected enrollment without a Core profile as disabled for governance display', () => {
    const state = deriveAgentGovernanceState(row({
      profile: undefined,
      source: { profile: 'MISSING', runtime: 'MISSING' },
      enrollment: {
        enrollmentId: 'enroll-rejected-002',
        claimedAgentId: 'agent-001',
        status: 'REJECTED'
      }
    }));

    assert.equal(state.enrollmentStatus, 'ENROLLMENT_REJECTED');
    assert.equal(state.enabledStatus, 'DISABLED');
    assert.equal(state.isCoreAssignableCandidate, false);
  });

  it('does not invent latency when Netty has not reported a latency metric', () => {
    assert.equal(getRuntimeLatencyLabel({ agentId: 'agent-001', connected: true }), 'not reported');
    assert.equal(getRuntimeLatencyLabel({ agentId: 'agent-001', connected: true, latencyMs: 12 }), '12 ms');
  });



  it('allows credential issue/rotation only for approved non-blocked profiles', () => {
    const approved = row({
      profile: {
        agentId: 'agent-001',
        approvalStatus: 'APPROVED',
        enabled: false,
        riskStatus: 'NORMAL',
        credential: { credentialId: 'cred-1', credentialStatus: 'REVOKED' }
      }
    });
    const quarantined = row({
      profile: {
        agentId: 'agent-001',
        approvalStatus: 'APPROVED',
        enabled: false,
        riskStatus: 'QUARANTINED',
        credential: { credentialId: 'cred-1', credentialStatus: 'REVOKED' }
      }
    });
    const rejected = row({
      profile: {
        agentId: 'agent-001',
        approvalStatus: 'REJECTED',
        enabled: false,
        riskStatus: 'NORMAL'
      }
    });

    assert.equal(canIssueCredentialForRow(approved), true);
    assert.equal(canIssueCredentialForRow(quarantined), false);
    assert.equal(canIssueCredentialForRow(rejected), false);
  });

  it('allows approved profiles to be revoked and revoked profiles to be approved/restored', () => {
    const approved = row({
      profile: {
        agentId: 'agent-001',
        approvalStatus: 'APPROVED',
        enabled: true,
        riskStatus: 'NORMAL',
        credential: { credentialId: 'cred-1', credentialStatus: 'ACTIVE' }
      }
    });
    const revoked = row({
      profile: {
        agentId: 'agent-001',
        approvalStatus: 'REVOKED',
        enabled: false,
        riskStatus: 'REVOKED',
        credential: { credentialId: 'cred-1', credentialStatus: 'REVOKED' }
      }
    });

    assert.equal(canRevokeCoreAgentProfile(approved), true);
    assert.equal(canApproveCoreAgentProfile(approved), false);
    assert.equal(canRevokeCoreAgentProfile(revoked), false);
    assert.equal(canApproveCoreAgentProfile(revoked), true);
  });

});