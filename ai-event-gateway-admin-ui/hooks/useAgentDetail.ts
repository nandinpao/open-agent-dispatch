'use client';

import { useCallback, useMemo, useState } from 'react';
import { isNotFoundOrUnsupportedApiError, requireCoreTenantContext } from '@/lib/api/client';
import { coreAdminApi } from '@/lib/api/coreAdminApi';
import { nettyRuntimeApi } from '@/lib/api/nettyRuntimeApi';
import { getPublicEnv } from '@/lib/constants/env';
import { getMockAgentDetail, getMockCommandResult } from '@/lib/mock/admin';
import { mergeAgentDashboardRows, summarizeRuntimes } from '@/lib/dashboard/agentMerge';
import { runtimeToEnrollmentRequest } from '@/lib/agents/enrollmentWorkflow';
import { appendManualDisconnectNotice } from '@/lib/runtime/rejectedConnectionSemantics';
import type { CommandResult } from '@/lib/types/admin';
import type { AgentEnrollmentApprovalRequest, AgentProfileUpdateRequest, AgentSecurityEvent, CoreAgentProfile, CoreAgentRuntimeCapabilityItem, CoreAgentRuntimeCapabilityProfile, CoreAgentRuntimeDescriptor, CoreAgentRuntimeLoadSnapshot, CoreAgentCapabilityAssignment, CoreAgentCapabilityCommand, CoreAgentRuntimeFeatureObservation, CoreAgentRuntimeFeatureTrust, CoreAgentRuntimeFeatureCommand, CoreAgentDispatchEligibility, CoreTaskRuntimeView, CoreAgentSecurityEnforcementPolicy, CoreAgentSecurityEnforcementPolicyUpdateRequest, CoreAgentSkillDefinition, CoreRecoveryGovernanceActionRequest, CoreAgentRemediationProposal, CoreAgentRemediationProposalRequest, CoreAgentRemediationWorkflow, CoreAgentRemediationWorkflowCreateRequest, CoreAgentRemediationWorkflowDecisionRequest, CoreAgentSetupReadinessResponse, CoreAgentOperationalView, CoreAgentLatestAuthFailureResponse, CoreAgentConnectionRepairActionsResponse, CoreAgentRuntimeBinding, CoreRuntimeResource, CoreAgentPoolView, CoreDispatchFlowView } from '@/lib/types/core';
import type { AgentDashboardRow } from '@/lib/types/dashboard';
import type { NettyAgentRuntime } from '@/lib/types/nettyRuntime';
import { usePollingResource } from '@/hooks/usePollingResource';

export interface AgentDetailBundle {
  profile?: CoreAgentProfile;
  runtime?: NettyAgentRuntime;
  runtimes: NettyAgentRuntime[];
  runtimeCapabilityProfile?: CoreAgentRuntimeCapabilityProfile;
  runtimeDescriptor?: CoreAgentRuntimeDescriptor;
  runtimeCapabilityItems: CoreAgentRuntimeCapabilityItem[];
  runtimeLoad?: CoreAgentRuntimeLoadSnapshot;
  dispatchFlows: CoreDispatchFlowView[];
  agentPools: CoreAgentPoolView[];
  capabilityAssignments: CoreAgentCapabilityAssignment[];
  runtimeFeatureObservations: CoreAgentRuntimeFeatureObservation[];
  runtimeFeatureTrusts: CoreAgentRuntimeFeatureTrust[];
  dispatchEligibility?: CoreAgentDispatchEligibility;
  setupReadiness?: CoreAgentSetupReadinessResponse;
  operationalView?: CoreAgentOperationalView;
  latestAuthFailure?: CoreAgentLatestAuthFailureResponse;
  connectionRepairActions?: CoreAgentConnectionRepairActionsResponse;
  runtimeBindings: CoreAgentRuntimeBinding[];
  row: AgentDashboardRow;
  tasks: CoreTaskRuntimeView[];
  securityEvents: AgentSecurityEvent[];
  securityPolicy?: CoreAgentSecurityEnforcementPolicy;
  skillDefinitions: CoreAgentSkillDefinition[];
  remediationProposal?: CoreAgentRemediationProposal;
  remediationWorkflows: CoreAgentRemediationWorkflow[];
  sourceErrors: Record<string, string | undefined>;
}


function mockTenantId(): string {
  try {
    return requireCoreTenantContext();
  } catch {
    return 'tenant-mock';
  }
}

type AgentCommand = 'PING' | 'DISCONNECT' | 'DISCONNECT_ALL' | 'ENABLE' | 'DISABLE' | 'SUSPEND' | 'REVOKE' | 'CREATE_ENROLLMENT' | 'APPROVE_OBSERVED' | 'UPDATE_PROFILE' | 'ENFORCE_DUPLICATE_SECURITY' | 'RESOLVE_DUPLICATE_SECURITY' | 'UPDATE_SECURITY_POLICY' | 'CLEAR_RUNTIME_BACKOFF' | 'CREATE_REMEDIATION_PROPOSAL' | 'SYNC_APPROVED_SKILLS' | 'CREATE_REMEDIATION_WORKFLOW' | 'APPROVE_REMEDIATION_WORKFLOW' | 'REJECT_REMEDIATION_WORKFLOW' | 'CANCEL_REMEDIATION_WORKFLOW' | 'EXECUTE_REMEDIATION_WORKFLOW' | 'REQUEST_CAPABILITY' | 'REMOVE_CAPABILITY' | 'APPROVE_CAPABILITY' | 'SUSPEND_CAPABILITY' | 'RESUME_CAPABILITY' | 'REVOKE_CAPABILITY' | 'CREATE_RUNTIME_BINDING' | 'ACTIVATE_RUNTIME_BINDING' | 'OBSERVE_RUNTIME_FEATURE' | 'VERIFY_RUNTIME_FEATURE' | 'TRUST_RUNTIME_FEATURE' | 'SUSPEND_RUNTIME_FEATURE' | 'RESUME_RUNTIME_FEATURE' | 'REVOKE_RUNTIME_FEATURE';

function mockBundle(agentId: string): AgentDetailBundle {
  const legacy = getMockAgentDetail(agentId);
  const profile: CoreAgentProfile = {
    agentId: legacy.agentId,
    agentName: legacy.displayName ?? legacy.agentId,
    agentType: legacy.agentType,
    description: legacy.description,
    approvalStatus: legacy.status === 'OFFLINE' ? 'SUSPENDED' : 'APPROVED',
    enabled: legacy.status !== 'OFFLINE',
    riskStatus: legacy.status === 'ERROR' ? 'QUARANTINED' : 'NORMAL',
    capabilities: (legacy.capabilities ?? []).map((capability) => ({
      capabilityCode: capability.capabilityId,
      capabilityVersion: capability.version,
      enabled: capability.status === 'ENABLED',
      metadata: { name: capability.name, description: capability.description, supportedActions: capability.supportedActions }
    })),
    credential: {
      credentialStatus: legacy.status === 'OFFLINE' ? 'MISSING' : 'ACTIVE',
      credentialVersion: 1
    },
    authorizationScopes: [{ tenantId: mockTenantId(), taskType: '*', systemCode: '*', enabled: true }],
    createdAt: legacy.connectedAt,
    updatedAt: legacy.lastSeenAt
  };

  const runtime: NettyAgentRuntime = {
    agentId: legacy.agentId,
    connected: legacy.status !== 'OFFLINE',
    authorizationState: legacy.status === 'OFFLINE' ? 'DISCONNECTED_BY_POLICY' : 'AUTHORIZED',
    gatewayNodeId: legacy.ownerNodeId ?? legacy.nodeId,
    nodeId: legacy.nodeId,
    transport: legacy.transport,
    connectedAt: legacy.connectedAt,
    lastHeartbeatAt: legacy.runtime.lastHeartbeatAt ?? legacy.lastSeenAt,
    lastSeenAt: legacy.lastSeenAt,
    activeTaskCount: legacy.runtime.activeTaskCount,
    currentTaskId: legacy.currentTaskId,
    latencyMs: legacy.runtime.averageLatencyMs
  };

  const row = mergeAgentDashboardRows([profile], [runtime])[0];
  return {
    profile,
    runtime,
    runtimes: [runtime],
    runtimeDescriptor: undefined,
    runtimeCapabilityItems: [],
    dispatchFlows: [],
    agentPools: [],
    capabilityAssignments: [],
    runtimeFeatureObservations: [],
    runtimeFeatureTrusts: [],
    dispatchEligibility: {
      agentId,
      eligible: true,
      dispatchStatus: 'ELIGIBLE',
      connectionStatus: 'IDLE',
      approvedProfiles: [],
      checks: [{ code: 'MOCK_ELIGIBILITY', status: 'PASS', blocking: false, message: 'Mock Agent is eligible.' }],
      nextActions: [],
      generatedAt: new Date().toISOString()
    },
    row,
    tasks: [],
    securityEvents: [],
    latestAuthFailure: undefined,
    runtimeBindings: [],
    skillDefinitions: [],
    remediationWorkflows: [],
    sourceErrors: {}
  };
}

function includesAgent(event: AgentSecurityEvent, agentId: string): boolean {
  return event.agentId === agentId || event.claimedAgentId === agentId;
}

function byRecentDate<T extends { updatedAt?: string; createdAt?: string; occurredAt?: string }>(left: T, right: T): number {
  const leftTime = Date.parse(left.updatedAt ?? left.occurredAt ?? left.createdAt ?? '') || 0;
  const rightTime = Date.parse(right.updatedAt ?? right.occurredAt ?? right.createdAt ?? '') || 0;
  return rightTime - leftTime;
}

function settledError(result: PromiseSettledResult<unknown>): string | undefined {
  if (result.status === 'fulfilled') return undefined;
  return result.reason instanceof Error ? result.reason.message : 'Unknown error';
}

function createResult(message: string): CommandResult {
  return { success: true, message, timestamp: new Date().toISOString() };
}

function runtimeActionContext(bundle: AgentDetailBundle | null | undefined, reason: string): { reason: string; gatewayNodeId?: string } {
  const gatewayNodeId = bundle?.runtime?.gatewayNodeId ?? bundle?.runtime?.nodeId;
  return gatewayNodeId ? { reason, gatewayNodeId } : { reason };
}

function runtimeGatewayNodeIds(bundle: AgentDetailBundle | null | undefined): string[] {
  const ids = new Set<string>();
  for (const runtime of bundle?.runtimes ?? []) {
    const id = runtime.gatewayNodeId ?? runtime.nodeId;
    if (id) ids.add(id);
  }
  return Array.from(ids);
}

export function useAgentDetail(agentId: string) {
  const [commandMessage, setCommandMessage] = useState<string | null>(null);
  const [commandRunning, setCommandRunning] = useState<AgentCommand | null>(null);

  const loader = useCallback(async (): Promise<AgentDetailBundle> => {
    const env = getPublicEnv();
    if (env.useMock) return mockBundle(agentId);

    const [preloadedProfileResult] = await Promise.allSettled([coreAdminApi.getAgent(agentId)]);
    const scopedTenantId = preloadedProfileResult.status === 'fulfilled'
      ? String(preloadedProfileResult.value.tenantId ?? '').trim()
      : '';

    const [profilesResult, operationalViewResult, setupReadinessResult, latestAuthFailureResult, connectionRepairActionsResult, runtimeBindingsResult, runtimeAgentsResult, tasksResult, securityEventsResult, runtimeCapabilityProfileResult, runtimeDescriptorResult, runtimeCapabilityItemsResult, runtimeLoadResult, capabilityAssignmentsResult, runtimeFeatureObservationsResult, runtimeFeatureTrustsResult, securityPolicyResult, skillDefinitionsResult, remediationProposalResult, remediationWorkflowsResult] = await Promise.allSettled([
      preloadedProfileResult.status === 'fulfilled'
        ? Promise.resolve(preloadedProfileResult.value)
        : Promise.reject(preloadedProfileResult.reason),
      coreAdminApi.getAgentOperationalView(agentId).catch((error) => {
        if (isNotFoundOrUnsupportedApiError(error)) return undefined;
        throw error;
      }),
      coreAdminApi.getAgentSetupReadiness(agentId).catch((error) => {
        if (isNotFoundOrUnsupportedApiError(error)) return undefined;
        throw error;
      }),
      coreAdminApi.getAgentLatestAuthFailure(agentId).catch((error) => {
        if (isNotFoundOrUnsupportedApiError(error)) return undefined;
        throw error;
      }),
      coreAdminApi.getAgentConnectionRepairActions(agentId).catch((error) => {
        if (isNotFoundOrUnsupportedApiError(error)) return undefined;
        throw error;
      }),
      coreAdminApi.getAgentRuntimeBindings(agentId).catch((error) => {
        if (isNotFoundOrUnsupportedApiError(error)) return [];
        throw error;
      }),
      nettyRuntimeApi.getClusterRuntimeAgents().catch(() => nettyRuntimeApi.getRuntimeAgents()),
      coreAdminApi.getTasksRuntimeView(),
      coreAdminApi.getSecurityEvents().catch(() => coreAdminApi.getAgentSecurityEvents()),
      coreAdminApi.getAgentRuntimeCapabilityProfile(agentId).catch((error) => {
        if (isNotFoundOrUnsupportedApiError(error)) return undefined;
        throw error;
      }),
      coreAdminApi.getAgentRuntimeDescriptor(agentId).catch((error) => {
        if (isNotFoundOrUnsupportedApiError(error)) return undefined;
        throw error;
      }),
      coreAdminApi.getAgentRuntimeCapabilities(agentId).catch((error) => {
        if (isNotFoundOrUnsupportedApiError(error)) return [];
        throw error;
      }),
      coreAdminApi.getAgentRuntimeLoad(agentId).catch((error) => {
        if (isNotFoundOrUnsupportedApiError(error)) return undefined;
        throw error;
      }),
      coreAdminApi.getAgentCapabilities(agentId).catch((error) => {
        if (isNotFoundOrUnsupportedApiError(error)) return [];
        throw error;
      }),
      coreAdminApi.getAgentRuntimeFeatureObservations(agentId).catch((error) => {
        if (isNotFoundOrUnsupportedApiError(error)) return [];
        throw error;
      }),
      coreAdminApi.getAgentRuntimeFeatureTrusts(agentId).catch((error) => {
        if (isNotFoundOrUnsupportedApiError(error)) return [];
        throw error;
      }),
      coreAdminApi.getAgentSecurityEnforcementPolicy(agentId).catch((error) => {
        if (isNotFoundOrUnsupportedApiError(error)) return undefined;
        throw error;
      }),
      coreAdminApi.getAgentSkillDefinitions().catch((error) => {
        if (isNotFoundOrUnsupportedApiError(error)) return [];
        throw error;
      }),
      coreAdminApi.getAgentRemediationProposal(agentId).catch((error) => {
        if (isNotFoundOrUnsupportedApiError(error)) return undefined;
        throw error;
      }),
      coreAdminApi.listAgentRemediationWorkflows(agentId).catch((error) => {
        if (isNotFoundOrUnsupportedApiError(error)) return [];
        throw error;
      })
    ]);


    const profile = profilesResult.status === 'fulfilled' ? profilesResult.value : undefined;
    const operationalView = operationalViewResult.status === 'fulfilled' ? operationalViewResult.value : undefined;
    const setupReadiness = operationalView?.setupReadiness ?? (setupReadinessResult.status === 'fulfilled' ? setupReadinessResult.value : undefined);
    const latestAuthFailure = latestAuthFailureResult.status === 'fulfilled' ? latestAuthFailureResult.value : undefined;
    const connectionRepairActions = connectionRepairActionsResult.status === 'fulfilled' ? connectionRepairActionsResult.value : undefined;
    const runtimeBindings = runtimeBindingsResult.status === 'fulfilled' ? runtimeBindingsResult.value : [];
    const runtimeAgents = runtimeAgentsResult.status === 'fulfilled' ? runtimeAgentsResult.value : [];
    const agentRuntimes = runtimeAgents.filter((candidate) => candidate.agentId === agentId);
    const runtime = agentRuntimes.find((candidate) => candidate.connected) ?? agentRuntimes[0];
    const tasks = tasksResult.status === 'fulfilled'
      ? tasksResult.value
        .filter((task) => task.assignedAgentId === agentId || (typeof task.payload === 'object' && task.payload !== null && (task.payload as Record<string, unknown>).agentId === agentId))
        .sort(byRecentDate)
      : [];
    const securityEvents = securityEventsResult.status === 'fulfilled'
      ? securityEventsResult.value.filter((event) => includesAgent(event, agentId)).sort(byRecentDate)
      : [];
    const runtimeCapabilityProfile = runtimeCapabilityProfileResult.status === 'fulfilled' ? runtimeCapabilityProfileResult.value : undefined;
    const runtimeDescriptor = runtimeDescriptorResult.status === 'fulfilled' ? runtimeDescriptorResult.value : undefined;
    const runtimeCapabilityItems = runtimeCapabilityItemsResult.status === 'fulfilled' ? runtimeCapabilityItemsResult.value : [];
    const runtimeLoad = runtimeLoadResult.status === 'fulfilled' ? runtimeLoadResult.value : undefined;
    const capabilityAssignments = capabilityAssignmentsResult.status === 'fulfilled' ? capabilityAssignmentsResult.value : [];
    const runtimeFeatureObservations = runtimeFeatureObservationsResult.status === 'fulfilled' ? runtimeFeatureObservationsResult.value : [];
    const runtimeFeatureTrusts = runtimeFeatureTrustsResult.status === 'fulfilled' ? runtimeFeatureTrustsResult.value : [];
    const dispatchEligibility = undefined;
    const securityPolicy = securityPolicyResult.status === 'fulfilled' ? securityPolicyResult.value : undefined;
    const skillDefinitions = skillDefinitionsResult.status === 'fulfilled' ? skillDefinitionsResult.value : [];
    const remediationProposal = remediationProposalResult.status === 'fulfilled' ? remediationProposalResult.value : undefined;
    const remediationWorkflows = remediationWorkflowsResult.status === 'fulfilled' ? remediationWorkflowsResult.value : [];
    const [dispatchFlowsResult, agentPoolsResult] = await Promise.allSettled([
      scopedTenantId ? coreAdminApi.getDispatchFlowsForAgent(scopedTenantId, agentId) : Promise.resolve([]),
      scopedTenantId ? coreAdminApi.getAgentPools(scopedTenantId) : Promise.resolve([]),
    ]);
    const dispatchFlows = dispatchFlowsResult.status === 'fulfilled'
      ? dispatchFlowsResult.value
      : [];
    const agentPools = agentPoolsResult.status === 'fulfilled'
      ? agentPoolsResult.value.filter((pool) => (pool.members ?? []).some((member) => member.agentId === agentId && String(member.memberStatus ?? 'ACTIVE').toUpperCase() !== 'RETIRED'))
      : [];

    const rows = mergeAgentDashboardRows(profile ? [profile] : [], agentRuntimes);
    const row = rows[0] ?? {
      agentId,
      profile,
      runtime,
      runtimes: agentRuntimes,
      runtimeSummary: summarizeRuntimes(agentRuntimes),
      approvalStatus: profile?.approvalStatus,
      enabled: profile?.enabled,
      riskStatus: profile?.riskStatus,
      connected: runtime?.connected ?? false,
      source: {
        profile: profile ? 'CORE' : 'MISSING',
        runtime: runtime ? 'NETTY' : 'MISSING'
      }
    };

    return {
      profile,
      runtime,
      runtimes: agentRuntimes,
      runtimeCapabilityProfile,
      runtimeDescriptor,
      runtimeCapabilityItems,
      runtimeLoad,
      dispatchFlows,
      agentPools,
      capabilityAssignments,
      runtimeFeatureObservations,
      runtimeFeatureTrusts,
      dispatchEligibility,
      setupReadiness,
      operationalView,
      latestAuthFailure,
      connectionRepairActions,
      runtimeBindings,
      row,
      tasks,
      securityEvents,
      securityPolicy,
      skillDefinitions,
      remediationProposal,
      remediationWorkflows,
      sourceErrors: {
        coreProfile: settledError(profilesResult),
        coreOperationalView: settledError(operationalViewResult),
        nettyRuntimeAgents: settledError(runtimeAgentsResult),
        coreTasks: settledError(tasksResult),
        coreSecurityEvents: settledError(securityEventsResult),
        coreSetupReadiness: settledError(setupReadinessResult),
        coreLatestAuthFailure: settledError(latestAuthFailureResult),
        coreConnectionRepairActions: settledError(connectionRepairActionsResult),
        coreRuntimeBindings: settledError(runtimeBindingsResult),
        coreCapabilityAssignments: settledError(capabilityAssignmentsResult),
        coreDispatchFlows: settledError(dispatchFlowsResult),
        coreAgentPools: settledError(agentPoolsResult),
      }
    };
  }, [agentId]);

  const resource = usePollingResource<AgentDetailBundle>(loader);

  const data = useMemo(() => resource.data, [resource.data]);

  async function runCommand(command: AgentCommand, action: () => Promise<unknown>): Promise<CommandResult> {
    setCommandRunning(command);
    try {
      const result = await action();
      const resultRecord = typeof result === 'object' && result !== null ? result as { message?: unknown } : {};
      const message = typeof resultRecord.message === 'string'
        ? resultRecord.message
        : `${command} command accepted for ${agentId}`;
      setCommandMessage(message);
      await resource.refresh();
      return typeof resultRecord.message === 'string' ? result as CommandResult : createResult(message);
    } finally {
      setCommandRunning(null);
    }
  }

  async function pingAgent(): Promise<CommandResult> {
    const env = getPublicEnv();
    return runCommand('PING', () => env.useMock ? Promise.resolve(getMockCommandResult(`Ping command accepted for ${agentId}`)) : nettyRuntimeApi.pingAgent(agentId));
  }

  async function disconnectAgent(): Promise<CommandResult> {
    const env = getPublicEnv();
    return runCommand('DISCONNECT', async () => {
      const result = env.useMock
        ? getMockCommandResult(`Disconnect command accepted for ${agentId}`)
        : await coreAdminApi.disconnectAgent(agentId, runtimeActionContext(resource.data, 'Manual Core runtime disconnect request from Agent detail'));
      return {
        ...result,
        message: appendManualDisconnectNotice(result.message, agentId, false)
      };
    });
  }

  async function disconnectAllAgentSessions(): Promise<CommandResult> {
    const env = getPublicEnv();
    return runCommand('DISCONNECT_ALL', async () => {
      const result = env.useMock
        ? getMockCommandResult(`Disconnect-all command accepted for ${agentId}`)
        : await coreAdminApi.disconnectAllAgentSessions(agentId, {
          reason: 'Manual Core cluster-aware disconnect-all request from Agent detail',
          gatewayNodeIds: runtimeGatewayNodeIds(resource.data)
        });
      return {
        ...result,
        message: appendManualDisconnectNotice(result.message, agentId, true)
      };
    });
  }

  async function enforceDuplicateRuntimeSecurity(revokeCredentials = false): Promise<CommandResult> {
    const env = getPublicEnv();
    return runCommand('ENFORCE_DUPLICATE_SECURITY', () => env.useMock
      ? Promise.resolve(getMockCommandResult(`Duplicate runtime security enforcement accepted for ${agentId}`))
      : coreAdminApi.enforceDuplicateRuntimeSecurity(agentId, {
        operatorId: 'admin-ui',
        reason: revokeCredentials
          ? 'Duplicate runtime sessions detected. Quarantine, revoke active credentials, and disconnect all sessions.'
          : 'Duplicate runtime sessions detected. Quarantine, require credential rotation, and disconnect all sessions.',
        gatewayNodeIds: runtimeGatewayNodeIds(resource.data),
        connectedCount: resource.data?.row.runtimeSummary?.connectedCount,
        disconnectAll: true,
        requireCredentialRotation: true,
        revokeCredentials
      }));
  }

  async function resolveDuplicateRuntimeSecurity(): Promise<CommandResult> {
    const env = getPublicEnv();
    return runCommand('RESOLVE_DUPLICATE_SECURITY', () => env.useMock
      ? Promise.resolve(getMockCommandResult(`Duplicate runtime security case resolved for ${agentId}`))
      : coreAdminApi.resolveDuplicateRuntimeSecurity(agentId, {
        operatorId: 'admin-ui',
        reason: 'Credential rotation deployed and duplicate runtime sessions remediated from Admin UI.',
        enableAfterRotation: true
      }));
  }

  async function enableAgent(): Promise<CommandResult> {
    const env = getPublicEnv();
    return runCommand('ENABLE', () => env.useMock ? Promise.resolve(getMockCommandResult(`Enable command accepted for ${agentId}`)) : coreAdminApi.enableAgent(agentId));
  }

  async function disableAgent(): Promise<CommandResult> {
    const env = getPublicEnv();
    return runCommand('DISABLE', () => env.useMock ? Promise.resolve(getMockCommandResult(`Disable command accepted for ${agentId}`)) : coreAdminApi.disableAgent(agentId, runtimeActionContext(resource.data, 'Agent disabled from Agent detail')));
  }

  async function suspendAgent(): Promise<CommandResult> {
    const env = getPublicEnv();
    return runCommand('SUSPEND', () => env.useMock ? Promise.resolve(getMockCommandResult(`Suspend command accepted for ${agentId}`)) : coreAdminApi.suspendAgent(agentId, runtimeActionContext(resource.data, 'Agent suspended from Agent detail')));
  }

  async function revokeAgent(): Promise<CommandResult> {
    const env = getPublicEnv();
    return runCommand('REVOKE', () => env.useMock ? Promise.resolve(getMockCommandResult(`Revoke credential command accepted for ${agentId}`)) : coreAdminApi.revokeAgent(agentId, runtimeActionContext(resource.data, 'Agent revoked from Agent detail')));
  }

  async function createEnrollmentFromRuntime(): Promise<CommandResult> {
    const env = getPublicEnv();
    return runCommand('CREATE_ENROLLMENT', async () => {
      if (env.useMock) return getMockCommandResult(`Core enrollment created for ${agentId}`);
      const runtime = resource.data?.runtime;
      if (!runtime) throw new Error(`Netty runtime not found for ${agentId}`);
      const created = await coreAdminApi.createAgentEnrollment(runtimeToEnrollmentRequest(runtime));
      return createResult(`Core enrollment ${created.enrollmentId} created for ${created.claimedAgentId ?? agentId}`);
    });
  }

  async function approveRuntimeObserved(body: AgentEnrollmentApprovalRequest): Promise<CommandResult> {
    const env = getPublicEnv();
    return runCommand('APPROVE_OBSERVED', async () => {
      if (env.useMock) return getMockCommandResult(`Runtime observed Agent approved for ${agentId}`);
      const runtime = resource.data?.runtime;
      if (!runtime) throw new Error(`Netty runtime not found for ${agentId}`);
      const created = await coreAdminApi.createAgentEnrollment(runtimeToEnrollmentRequest(runtime));
      const profile = await coreAdminApi.approveAgentEnrollment(created.enrollmentId, body);
      return createResult(`Core enrollment ${created.enrollmentId} approved. Agent profile ${profile.agentId} is now authoritative.`);
    });
  }

  async function updateAgentProfile(body: AgentProfileUpdateRequest): Promise<CommandResult> {
    const env = getPublicEnv();
    return runCommand('UPDATE_PROFILE', () => env.useMock ? Promise.resolve(getMockCommandResult(`Agent profile updated for ${agentId}`)) : coreAdminApi.updateAgentProfile(agentId, body));
  }

  async function updateSecurityEnforcementPolicy(body: CoreAgentSecurityEnforcementPolicyUpdateRequest): Promise<CommandResult> {
    const env = getPublicEnv();
    return runCommand('UPDATE_SECURITY_POLICY', () => env.useMock
      ? Promise.resolve(getMockCommandResult(`Security enforcement policy updated for ${agentId}`))
      : coreAdminApi.updateAgentSecurityEnforcementPolicy(agentId, { ...body, operatorId: body.operatorId ?? 'admin-ui' }));
  }

  async function clearRuntimeBackoff(body?: CoreRecoveryGovernanceActionRequest): Promise<CommandResult> {
    const env = getPublicEnv();
    return runCommand('CLEAR_RUNTIME_BACKOFF', () => env.useMock
      ? Promise.resolve(getMockCommandResult(`Runtime backoff cleared for ${agentId}`))
      : coreAdminApi.clearRuntimeBackoff(agentId, body ?? {
        operatorId: 'admin-ui',
        reason: 'Manual clear runtime backoff from Agent detail',
        riskAcknowledged: true,
        confirmationPhrase: 'CONFIRM_RECOVERY_ACTION',
        requestId: `admin-ui-clear-backoff-${Date.now()}`
      }));
  }



  async function createAgentRemediationProposal(body?: CoreAgentRemediationProposalRequest): Promise<CommandResult> {
    const env = getPublicEnv();
    return runCommand('CREATE_REMEDIATION_PROPOSAL', () => env.useMock
      ? Promise.resolve(getMockCommandResult(`Remediation proposal generated for ${agentId}`))
      : coreAdminApi.createAgentRemediationProposal(agentId, body ?? {
        operatorId: 'admin-ui',
        reason: 'Agent remediation proposal generated from Admin UI.',
        persistEvent: true
      }));
  }



  async function createAgentRemediationWorkflow(body?: CoreAgentRemediationWorkflowCreateRequest): Promise<CommandResult> {
    const env = getPublicEnv();
    return runCommand('CREATE_REMEDIATION_WORKFLOW', () => env.useMock
      ? Promise.resolve(getMockCommandResult(`Remediation workflow created for ${agentId}`))
      : coreAdminApi.createAgentRemediationWorkflow(agentId, body ?? {
        operatorId: 'admin-ui',
        reason: 'Agent remediation workflow created from Admin UI.',
        riskAcknowledged: false
      }));
  }

  async function decideAgentRemediationWorkflow(command: 'APPROVE_REMEDIATION_WORKFLOW' | 'REJECT_REMEDIATION_WORKFLOW' | 'CANCEL_REMEDIATION_WORKFLOW' | 'EXECUTE_REMEDIATION_WORKFLOW', workflowId: string, body?: CoreAgentRemediationWorkflowDecisionRequest): Promise<CommandResult> {
    const env = getPublicEnv();
    const request = body ?? { operatorId: 'admin-ui', reason: `${command} from Admin UI.`, dryRun: command === 'EXECUTE_REMEDIATION_WORKFLOW' ? false : undefined };
    return runCommand(command, () => {
      if (env.useMock) return Promise.resolve(getMockCommandResult(`${command} accepted for ${workflowId}`));
      if (command === 'APPROVE_REMEDIATION_WORKFLOW') return coreAdminApi.approveAgentRemediationWorkflow(agentId, workflowId, request);
      if (command === 'REJECT_REMEDIATION_WORKFLOW') return coreAdminApi.rejectAgentRemediationWorkflow(agentId, workflowId, request);
      if (command === 'CANCEL_REMEDIATION_WORKFLOW') return coreAdminApi.cancelAgentRemediationWorkflow(agentId, workflowId, request);
      return coreAdminApi.executeAgentRemediationWorkflow(agentId, workflowId, request);
    });
  }

  async function syncApprovedSkillsFromRemediation(skillCodes: string[]): Promise<CommandResult> {
    const env = getPublicEnv();
    const normalized = Array.from(new Set((skillCodes ?? []).filter((value) => value && value.trim()).map((value) => value.trim())));
    return runCommand('SYNC_APPROVED_SKILLS', () => env.useMock
      ? Promise.resolve(getMockCommandResult(`Approved skill sync accepted for ${agentId}`))
      : coreAdminApi.syncAgentApprovedSkillsAndCapabilities(agentId, {
        skillCodes: normalized,
        enabled: true,
        syncProfileCapabilities: true,
        operatorId: 'admin-ui',
        reason: 'P5 remediation workflow synchronized approved skills and governance capabilities.'
      }));
  }




  async function createRuntimeBindingFromCurrentRuntime(): Promise<CommandResult> {
    const env = getPublicEnv();
    return runCommand('CREATE_RUNTIME_BINDING', async () => {
      if (env.useMock) return getMockCommandResult(`Runtime binding created for ${agentId}`);
      const profile = resource.data?.profile;
      const runtime = resource.data?.runtime;
      const gatewayNodeId = runtime?.gatewayNodeId ?? runtime?.nodeId ?? 'gateway-node-unknown';
      const runtimeCode = `${agentId}-${gatewayNodeId}-runtime`.trim().replace(/[^A-Za-z0-9]+/g, '_').replace(/^_+|_+$/g, '').toUpperCase();
      const runtimeId = `runtime-${runtimeCode.toLowerCase().replace(/_/g, '-')}`;
      const tenantId = requireCoreTenantContext(profile?.tenantId);
      const resourceBody: CoreRuntimeResource = {
        tenantId,
        runtimeId,
        runtimeCode,
        runtimeName: `${agentId} runtime on ${gatewayNodeId}`,
        runtimeType: profile?.agentType ?? 'AGENT_RUNTIME',
        connectorType: 'GATEWAY_RUNTIME',
        executionHost: gatewayNodeId,
        environment: 'runtime-observation',
        trustStatus: 'TRUSTED',
        status: 'ACTIVE',
        capacityLimit: Math.max(1, runtime?.activeTaskCount === undefined ? 1 : (runtime.activeTaskCount + 1)),
        metadata: {
          source: 'Agent Detail Runtime Binding action',
          gatewayNodeId,
          agentSessionId: runtime?.sessionId,
          dispatchAuthority: 'ACTIVE_RUNTIME_BINDING'
        }
      };
      await coreAdminApi.upsertRuntimeResource(runtimeId, resourceBody, tenantId);
      const binding = await coreAdminApi.upsertAgentRuntimeBinding(agentId, {
        tenantId,
        agentId,
        runtimeId,
        runtimeCode,
        bindingStatus: 'ACTIVE',
        verifiedBy: 'admin-ui',
        approvedBy: 'admin-ui',
        capacityLimit: resourceBody.capacityLimit,
        dataScope: 'STANDARD',
        riskLimit: 'MIDDLE',
        metadata: {
          source: 'Agent Detail Runtime Binding action',
          gatewayNodeId,
          agentSessionId: runtime?.sessionId,
          dispatchAuthority: 'ACTIVE_RUNTIME_BINDING'
        }
      });
      return createResult(`Runtime binding ${binding.bindingId ?? runtimeId} is ${binding.bindingStatus ?? 'ACTIVE'} for ${agentId}`);
    });
  }

  async function activateRuntimeBinding(bindingId: string): Promise<CommandResult> {
    const env = getPublicEnv();
    return runCommand('ACTIVATE_RUNTIME_BINDING', async () => {
      if (env.useMock) return getMockCommandResult(`Runtime binding ${bindingId} activated for ${agentId}`);
      const binding = await coreAdminApi.activateAgentRuntimeBinding(agentId, bindingId, {
        approvedBy: 'admin-ui',
        metadata: {
          operatorId: 'admin-ui',
          reason: 'Activated from Agent detail Runtime Binding panel',
          dispatchAuthority: 'ACTIVE_RUNTIME_BINDING'
        }
      });
      return createResult(`Runtime binding ${binding.bindingId ?? bindingId} is ${binding.bindingStatus ?? 'ACTIVE'} for ${agentId}`);
    });
  }

  function requestAgentCapability(body: CoreAgentCapabilityCommand): Promise<CommandResult> {
    return runCommand('REQUEST_CAPABILITY', () => coreAdminApi.requestAgentCapability(agentId, body));
  }

  function removeAgentCapability(assignmentId: string, request?: CoreAgentCapabilityCommand): Promise<CommandResult> {
    return runCommand('REMOVE_CAPABILITY', () => coreAdminApi.removeAgentCapability(agentId, assignmentId, request));
  }

  function approveAgentCapability(assignmentId: string, body?: CoreAgentCapabilityCommand): Promise<CommandResult> {
    return runCommand('APPROVE_CAPABILITY', () => coreAdminApi.approveAgentCapability(agentId, assignmentId, body));
  }

  function suspendAgentCapability(assignmentId: string, body?: CoreAgentCapabilityCommand): Promise<CommandResult> {
    return runCommand('SUSPEND_CAPABILITY', () => coreAdminApi.suspendAgentCapability(agentId, assignmentId, body));
  }

  function resumeAgentCapability(assignmentId: string, body?: CoreAgentCapabilityCommand): Promise<CommandResult> {
    return runCommand('RESUME_CAPABILITY', () => coreAdminApi.resumeAgentCapability(agentId, assignmentId, body));
  }

  function revokeAgentCapability(assignmentId: string, body?: CoreAgentCapabilityCommand): Promise<CommandResult> {
    return runCommand('REVOKE_CAPABILITY', () => coreAdminApi.revokeAgentCapability(agentId, assignmentId, body));
  }


  function observeAgentRuntimeFeature(body: CoreAgentRuntimeFeatureCommand): Promise<CommandResult> {
    return runCommand('OBSERVE_RUNTIME_FEATURE', () => coreAdminApi.observeAgentRuntimeFeature(agentId, body));
  }

  function verifyAgentRuntimeFeature(trustId: string, body?: CoreAgentRuntimeFeatureCommand): Promise<CommandResult> {
    return runCommand('VERIFY_RUNTIME_FEATURE', () => coreAdminApi.verifyAgentRuntimeFeature(agentId, trustId, body));
  }

  function trustAgentRuntimeFeature(trustId: string, body?: CoreAgentRuntimeFeatureCommand): Promise<CommandResult> {
    return runCommand('TRUST_RUNTIME_FEATURE', () => coreAdminApi.trustAgentRuntimeFeature(agentId, trustId, body));
  }

  function suspendAgentRuntimeFeatureTrust(trustId: string, body?: CoreAgentRuntimeFeatureCommand): Promise<CommandResult> {
    return runCommand('SUSPEND_RUNTIME_FEATURE', () => coreAdminApi.suspendAgentRuntimeFeatureTrust(agentId, trustId, body));
  }

  function resumeAgentRuntimeFeatureTrust(trustId: string, body?: CoreAgentRuntimeFeatureCommand): Promise<CommandResult> {
    return runCommand('RESUME_RUNTIME_FEATURE', () => coreAdminApi.resumeAgentRuntimeFeatureTrust(agentId, trustId, body));
  }

  function revokeAgentRuntimeFeatureTrust(trustId: string, body?: CoreAgentRuntimeFeatureCommand): Promise<CommandResult> {
    return runCommand('REVOKE_RUNTIME_FEATURE', () => coreAdminApi.revokeAgentRuntimeFeatureTrust(agentId, trustId, body));
  }

  return {
    ...resource,
    data,
    commandMessage,
    commandRunning,
    pingAgent,
    disconnectAgent,
    disconnectAllAgentSessions,
    enforceDuplicateRuntimeSecurity,
    resolveDuplicateRuntimeSecurity,
    enableAgent,
    disableAgent,
    suspendAgent,
    revokeAgent,
    createEnrollmentFromRuntime,
    approveRuntimeObserved,
    updateAgentProfile,
    updateSecurityEnforcementPolicy,
    clearRuntimeBackoff,
    requestAgentCapability,
    removeAgentCapability,
    approveAgentCapability,
    suspendAgentCapability,
    resumeAgentCapability,
    revokeAgentCapability,
    createRuntimeBindingFromCurrentRuntime,
    activateRuntimeBinding,
    observeAgentRuntimeFeature,
    verifyAgentRuntimeFeature,
    trustAgentRuntimeFeature,
    suspendAgentRuntimeFeatureTrust,
    resumeAgentRuntimeFeatureTrust,
    revokeAgentRuntimeFeatureTrust,
    createAgentRemediationProposal,
    createAgentRemediationWorkflow,
    decideAgentRemediationWorkflow,
    syncApprovedSkillsFromRemediation
  };
}
