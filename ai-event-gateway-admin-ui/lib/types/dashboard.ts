import type { AgentEnrollmentRequest, CoreAgentCertificationRun, CoreAgentDispatchEligibility, CoreAgentProfile, CoreAgentQualification, CoreTaskRuntimeView, CoreDispatchFlowView } from '@/lib/types/core';
import type { NettyAgentRuntime, NettyDeliveryRuntime, NettyRuntimeSnapshot } from '@/lib/types/nettyRuntime';

export interface AgentRuntimeSummary {
  connectedCount: number;
  sessionCount: number;
  gatewayNodeIds: string[];
  duplicateRuntimeDetected: boolean;
  connectedGatewayNodeIds: string[];
}

export interface AgentDashboardRow {
  agentId: string;
  profile?: CoreAgentProfile;
  runtime?: NettyAgentRuntime;
  runtimes?: NettyAgentRuntime[];
  runtimeSummary?: AgentRuntimeSummary;
  enrollment?: AgentEnrollmentRequest;
  qualifications?: CoreAgentQualification[];
  dispatchEligibility?: CoreAgentDispatchEligibility;
  certificationRuns?: CoreAgentCertificationRun[];
  dispatchFlows?: CoreDispatchFlowView[];
  approvalStatus?: string;
  enabled?: boolean;
  riskStatus?: string;
  connected?: boolean;
  source: {
    profile: 'CORE' | 'MISSING';
    runtime: 'NETTY' | 'MISSING';
  };
}

export interface TaskDispatchDashboardRow {
  task: CoreTaskRuntimeView;
  delivery?: NettyDeliveryRuntime;
}

export interface DualDashboardSnapshot {
  generatedAt: string;
  core?: unknown;
  netty?: NettyRuntimeSnapshot;
  agents?: AgentDashboardRow[];
  tasks?: TaskDispatchDashboardRow[];
}
