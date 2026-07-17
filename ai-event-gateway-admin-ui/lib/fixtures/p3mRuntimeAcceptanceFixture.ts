export const p3mRuntimeAcceptanceFixture = {
  id: 'P3M_ENFORCE_RUNTIME_ACCEPTANCE',
  mode: 'ENFORCE',
  taskId: 'p3k-enforce-cms-review',
  coreBaseUrlEnv: 'P3M_CORE_BASE_URL',
  adminBaseUrlEnv: 'P3M_ADMIN_BASE_URL',
  outputEnv: 'P3M_ACCEPTANCE_OUTPUT',
  endpoints: {
    coreStatus: '/api/core/status',
    eligibilityV2: '/admin/tasks/p3k-enforce-cms-review/eligible-agents-v2?limit=500',
    routingDecisions: '/admin/tasks/p3k-enforce-cms-review/routing-decisions',
    migrationReadiness: '/settings/migration-readiness',
    dispatchSimulator: '/testing/dispatch-simulator',
  },
  expected: {
    eligibleAgentIds: ['agent-p3k-ready'],
    blockedCodes: [
      'P3H_ACTIVE_RUNTIME_BINDING_REQUIRED',
      'P3H_REQUIRED_CAPABILITY_MISSING',
      'P3H_REQUIRED_RUNTIME_FEATURE_NOT_TRUSTED',
      'P3H_QUALITY_RULE_FAILED',
    ],
    scoreBreakdownKeys: [
      'eligibilityEngineMode',
      'eligibilityV2Applied',
      'eligibilityV2CandidateStatus',
      'eligibilityV2BlockingReasons',
      'eligibilityV2ScoreBreakdown',
    ],
  },
  releaseArtifact: {
    fileName: 'p3m-enforce-runtime-acceptance.json',
    requiredTopLevelKeys: ['generatedAt', 'mode', 'taskId', 'coreBaseUrl', 'checks', 'summary'],
    requiredChecks: [
      'core-status',
      'eligibility-v2-live-api',
      'eligible-candidate-present',
      'blocked-codes-present',
      'routing-decision-json-optional',
    ],
  },
  cutoverSequence: ['LEGACY_ONLY', 'SHADOW', 'WARN', 'ENFORCE'],
  rollbackTarget: 'WARN',
} as const;
