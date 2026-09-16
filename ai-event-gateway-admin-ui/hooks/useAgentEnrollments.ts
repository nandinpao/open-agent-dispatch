'use client';

import { useCallback, useState } from 'react';
import { coreAdminApi } from '@/lib/api/coreAdminApi';
import { nettyRuntimeApi } from '@/lib/api/nettyRuntimeApi';
import type { AgentEnrollmentApprovalRequest, AgentEnrollmentRequest, CoreAgentProfile } from '@/lib/types/core';
import { runtimeToEnrollmentCandidate, isRuntimeObservedEnrollment } from '@/lib/agents/enrollmentWorkflow';
import { usePollingResource } from '@/hooks/usePollingResource';

interface AgentEnrollmentData {
  enrollments: AgentEnrollmentRequest[];
  coreError?: string;
  coreAgentsError?: string;
  runtimeError?: string;
}

async function loadEnrollments(): Promise<AgentEnrollmentData> {
  const [coreResult, coreAgentsResult, runtimeResult] = await Promise.allSettled([
    coreAdminApi.getAgentEnrollments(),
    coreAdminApi.getAgentsRuntimeView().catch(() => coreAdminApi.getAgents()),
    nettyRuntimeApi.getClusterRuntimeAgents().catch(() => nettyRuntimeApi.getRuntimeAgents())
  ]);

  const coreEnrollments = coreResult.status === 'fulfilled' ? coreResult.value : [];
  const coreAgents: CoreAgentProfile[] = coreAgentsResult.status === 'fulfilled' ? coreAgentsResult.value : [];
  const runtimeAgents = runtimeResult.status === 'fulfilled' ? runtimeResult.value : [];

  const coreEnrollmentAgentIds = new Set(coreEnrollments.map((enrollment) => enrollment.claimedAgentId).filter(Boolean));
  const coreProfileAgentIds = new Set(coreAgents.map((profile) => profile.agentId).filter(Boolean));
  const observedCandidates = runtimeAgents
    .filter((runtime) => !coreEnrollmentAgentIds.has(runtime.agentId) && !coreProfileAgentIds.has(runtime.agentId))
    .map(runtimeToEnrollmentCandidate);

  return {
    enrollments: [...coreEnrollments, ...observedCandidates],
    coreError: coreResult.status === 'rejected' ? (coreResult.reason instanceof Error ? coreResult.reason.message : String(coreResult.reason)) : undefined,
    coreAgentsError: coreAgentsResult.status === 'rejected' ? (coreAgentsResult.reason instanceof Error ? coreAgentsResult.reason.message : String(coreAgentsResult.reason)) : undefined,
    runtimeError: runtimeResult.status === 'rejected' ? (runtimeResult.reason instanceof Error ? runtimeResult.reason.message : String(runtimeResult.reason)) : undefined
  };
}

export { isRuntimeObservedEnrollment } from '@/lib/agents/enrollmentWorkflow';

export function useAgentEnrollments() {
  const [commandMessage, setCommandMessage] = useState<string | null>(null);

  const loader = useCallback(loadEnrollments, []);
  const resource = usePollingResource<AgentEnrollmentData>(loader);

  async function materializeRuntimeObservedEnrollment(enrollment: AgentEnrollmentRequest): Promise<AgentEnrollmentRequest> {
    const created = await coreAdminApi.createAgentEnrollment({
      claimedAgentId: enrollment.claimedAgentId,
      tenantId: enrollment.tenantId,
      agentName: enrollment.agentName,
      agentType: enrollment.agentType,
      submittedMetadata: enrollment.submittedMetadata ?? enrollment.submittedMetadataJson,
      evidence: enrollment.evidence ?? enrollment.evidenceJson,
      fingerprint: enrollment.fingerprint,
      remoteAddress: enrollment.remoteAddress,
      submittedAt: enrollment.submittedAt
    });
    return created;
  }

  async function createEnrollmentFromObserved(enrollment: AgentEnrollmentRequest): Promise<AgentEnrollmentRequest> {
    const result = isRuntimeObservedEnrollment(enrollment)
      ? await materializeRuntimeObservedEnrollment(enrollment)
      : enrollment;
    setCommandMessage(`Agent ${result.claimedAgentId ?? result.enrollmentId} created Core enrollment ${result.enrollmentId}.`);
    await resource.refresh();
    return result;
  }

  async function approveEnrollment(enrollment: AgentEnrollmentRequest, body?: AgentEnrollmentApprovalRequest): Promise<CoreAgentProfile> {
    const coreEnrollment = isRuntimeObservedEnrollment(enrollment)
      ? await materializeRuntimeObservedEnrollment(enrollment)
      : enrollment;
    const result = await coreAdminApi.approveAgentEnrollment(coreEnrollment.enrollmentId, body);
    setCommandMessage(`Agent enrollment ${coreEnrollment.enrollmentId} approved,Agent profile ${result.agentId} created/update.`);
    await resource.refresh();
    return result;
  }

  async function rejectEnrollment(enrollment: AgentEnrollmentRequest, reviewComment?: string): Promise<AgentEnrollmentRequest> {
    const coreEnrollment = isRuntimeObservedEnrollment(enrollment)
      ? await materializeRuntimeObservedEnrollment(enrollment)
      : enrollment;
    const result = await coreAdminApi.rejectAgentEnrollment(coreEnrollment.enrollmentId, { reason: reviewComment || 'Rejected from Admin UI' });
    setCommandMessage(`Agent enrollment ${coreEnrollment.enrollmentId} reject.`);
    await resource.refresh();
    return result;
  }

  return {
    ...resource,
    data: resource.data?.enrollments ?? [],
    sourceErrors: {
      coreEnrollments: resource.data?.coreError,
      coreAgents: resource.data?.coreAgentsError,
      nettyRuntimeAgents: resource.data?.runtimeError
    },
    commandMessage,
    createEnrollmentFromObserved,
    approveEnrollment,
    rejectEnrollment
  };
}
