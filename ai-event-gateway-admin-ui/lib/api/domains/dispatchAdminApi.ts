import { coreAdminApi } from '@/lib/api/coreAdminApi';

/**
 * Phase 0I Dispatch-domain facade. The compatibility API remains available while
 * current product surfaces migrate by domain instead of importing a 3,000-line client.
 */
export const dispatchAdminApi = {
  getDispatchFlowRuleConflicts: coreAdminApi.getDispatchFlowRuleConflicts.bind(coreAdminApi),
  getDispatchFlowCapabilityBridge: coreAdminApi.getDispatchFlowCapabilityBridge.bind(coreAdminApi),
  refreshDispatchFlowCapabilityBridge: coreAdminApi.refreshDispatchFlowCapabilityBridge.bind(coreAdminApi),
  getDispatchFlowLegacyEquivalence: coreAdminApi.getDispatchFlowLegacyEquivalence.bind(coreAdminApi),
  getDispatchFlowLegacyEquivalenceReadiness: coreAdminApi.getDispatchFlowLegacyEquivalenceReadiness.bind(coreAdminApi),
  backfillDispatchFlowLegacyEquivalence: coreAdminApi.backfillDispatchFlowLegacyEquivalence.bind(coreAdminApi),
  getDispatchFlowAgentOptions: coreAdminApi.getDispatchFlowAgentOptions.bind(coreAdminApi),
  getCapabilities: coreAdminApi.getCapabilities.bind(coreAdminApi),
  getCanonicalCapabilities: coreAdminApi.getCanonicalCapabilities.bind(coreAdminApi),
  getAgentCapabilities: coreAdminApi.getAgentCapabilities.bind(coreAdminApi),
  getAgentQualityWindows: coreAdminApi.getAgentQualityWindows.bind(coreAdminApi),
  updateDispatchFlow: coreAdminApi.updateDispatchFlow.bind(coreAdminApi),
  createAgentPool: coreAdminApi.createAgentPool.bind(coreAdminApi),
  updateAgentPool: coreAdminApi.updateAgentPool.bind(coreAdminApi),
  simulateDispatch: coreAdminApi.simulateDispatch.bind(coreAdminApi),
  createDispatchFlowRealTestEvent: coreAdminApi.createDispatchFlowRealTestEvent.bind(coreAdminApi),
  getAgentPoolCapabilityPolicies: coreAdminApi.getAgentPoolCapabilityPolicies.bind(coreAdminApi),
  upsertAgentPoolCapabilityPolicy: coreAdminApi.upsertAgentPoolCapabilityPolicy.bind(coreAdminApi),
  generateAdvisoryRecommendations: coreAdminApi.generateAdvisoryRecommendations.bind(coreAdminApi),
  getAdvisoryRecommendations: coreAdminApi.getAdvisoryRecommendations.bind(coreAdminApi),
  acceptAdvisoryRecommendation: coreAdminApi.acceptAdvisoryRecommendation.bind(coreAdminApi),
  rejectAdvisoryRecommendation: coreAdminApi.rejectAdvisoryRecommendation.bind(coreAdminApi),
  getAdvancedSelectionStrategyContracts: coreAdminApi.getAdvancedSelectionStrategyContracts.bind(coreAdminApi),
} as const;
