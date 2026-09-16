export type RuntimeCapabilityState = 'DISABLED' | 'SHADOW' | 'PILOT' | 'ENABLED';

export interface RuntimeCapabilitySnapshot {
  contractVersion: '4.0';
  generatedAt: string;
  authentication: {
    sessionPath: '/api/session';
    legacyPasswordAdapterEnabled: boolean;
  };
  surfaces: {
    iamAdministration: RuntimeCapabilityState;
    resourceAccessAdministration: RuntimeCapabilityState;
    uiCapabilityProjection: RuntimeCapabilityState;
    enforcementActivation: RuntimeCapabilityState;
    a2aOperations: RuntimeCapabilityState;
    issueTracking: RuntimeCapabilityState;
  };
}

export function runtimeSurfaceAvailable(state: RuntimeCapabilityState): boolean {
  return state !== 'DISABLED';
}
