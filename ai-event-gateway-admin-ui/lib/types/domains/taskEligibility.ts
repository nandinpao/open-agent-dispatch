import type { CoreDispatchEligibilityCheck } from '@/lib/types/core';

/**
 * Canonical Task dispatch requirement and eligibility read-model types.
 */
export interface CoreTaskDispatchRequirementProfile {
  profileCode: string;
  profileName?: string;
  agentType?: string;
  allowedTaskTypes?: string[];
  allowedIssueProviders?: string[];
  requiredRuntimeFeatures?: string[];
  requiredCapabilities?: string[];
  requiredPolicyCodes?: string[];
  toolPolicy?: string;
  riskLevelLimit?: string;
  requiresCertification?: boolean;
  requiresHumanApproval?: boolean;
}

export interface CoreTaskDispatchRequirements {
  taskId?: string;
  taskType?: string;
  sourceSystem?: string;
  tenantId?: string;
  requiredProfiles?: string[];
  profiles?: CoreTaskDispatchRequirementProfile[];
  requiredRuntimeFeatures?: string[];
  requiredCapabilities?: string[];
  requiredPolicyCodes?: string[];
  taskDefinitionIds?: string[];
  allowedIssueProviders?: string[];
  toolPolicies?: string[];
  riskLevelLimit?: string;
  requirementSource?: string;
  generatedAt?: string;
}

export interface CoreEligibleAgentCandidate {
  agentId: string;
  agentType?: string;
  profileCode?: string;
  matchedProfiles?: string[];
  score?: number;
  eligible?: boolean;
  dispatchStatus?: string;
  reason?: string;
  checks?: CoreDispatchEligibilityCheck[];
}

export interface CoreTaskEligibleAgentsResponse {
  taskId?: string;
  requirements?: CoreTaskDispatchRequirements;
  eligibleAgents?: CoreEligibleAgentCandidate[];
  blockedAgents?: CoreEligibleAgentCandidate[];
  generatedAt?: string;
}

export interface CoreDispatchEligibilityV2BlockingReason {
  code?: string;
  message?: string;
  severity?: string;
  policyCode?: string;
  ruleId?: string;
  details?: Record<string, unknown>;
}

export interface CoreDispatchEligibilityV2ScoreBreakdown {
  factorName?: string;
  weight?: number;
  contribution?: number;
  direction?: string;
  message?: string;
  details?: Record<string, unknown>;
}

export interface CoreDispatchEligibilityV2PolicyMatch {
  policyCode?: string;
  policyName?: string;
  policyStatus?: string;
  matchedScopes?: string[];
  requiredCapabilities?: string[];
  requiredRuntimeFeatures?: string[];
  qualityRules?: string[];
  scoringRules?: string[];
}

export interface CoreDispatchEligibilityV2Candidate {
  agentId?: string;
  runtimeId?: string;
  bindingId?: string;
  supplyProfileId?: string;
  supplyProfileCode?: string;
  serviceRole?: string;
  serviceLevel?: string;
  qualityGrade?: string;
  eligible?: boolean;
  dispatchStatus?: string;
  score?: number;
  matchedPolicyCodes?: string[];
  matchedCapabilities?: string[];
  matchedRuntimeFeatures?: string[];
  blockingReasons?: CoreDispatchEligibilityV2BlockingReason[];
  scoreBreakdown?: CoreDispatchEligibilityV2ScoreBreakdown[];
}

export interface CoreDispatchEligibilityV2Response {
  taskId?: string;
  tenantId?: string;
  sourceSystem?: string;
  taskType?: string;
  engineMode?: string;
  requirementSource?: string;
  applicablePolicies?: CoreDispatchEligibilityV2PolicyMatch[];
  eligibleCandidates?: CoreDispatchEligibilityV2Candidate[];
  blockedCandidates?: CoreDispatchEligibilityV2Candidate[];
  globalBlockingReasons?: CoreDispatchEligibilityV2BlockingReason[];
  generatedAt?: string;
}
