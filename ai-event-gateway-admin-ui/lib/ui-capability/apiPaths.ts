export const UI_CAPABILITY_API_PATHS = {
  pageBootstrap: '/api/ui/bootstrap/{pageContext}',
  capabilityBatch: '/api/ui/capabilities:batch',
  listCapabilityBatch: '/api/ui/list-capabilities:batch',
  currentSecurityEpoch: '/api/ui/security-epochs/current',
  sessionRiskState: '/api/ui/session-risk-state',
} as const;

export const UI_CAPABILITY_BATCH_LIMITS = {
  genericContexts: 20,
  genericMaxPayloadBytes: 128 * 1024,
  listRowContexts: 50,
  listRowMaxPayloadBytes: 256 * 1024,
} as const;
