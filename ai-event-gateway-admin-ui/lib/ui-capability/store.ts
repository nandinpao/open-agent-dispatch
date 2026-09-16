import type {
  UiCapability,
  UiCapabilityEnvelope,
  UiPageBootstrap,
  UiReasonCategory,
} from '@/lib/ui-capability/contracts';

export type UiCapabilityContextStatus = 'READY' | 'HYDRATING' | 'STALE' | 'ERROR';
export type UiCapabilitySource = 'BOOTSTRAP' | 'HYDRATION';

export interface UiCapabilityContextIdentity {
  contextId: string;
  resourceId: string;
  tenantId: string;
  principalEpoch: number;
  catalogRevision: number;
  policyVersion: number;
  resourceRefHash?: string;
  resourceVersion?: number;
  expiresAt: string;
  refreshAfter: string;
}

export interface UiCapabilityContextRecord {
  identity: UiCapabilityContextIdentity;
  capabilities: ReadonlyMap<string, UiCapability>;
  sourceByAction: ReadonlyMap<string, UiCapabilitySource>;
  status: UiCapabilityContextStatus;
  hydrationRequestId?: string;
  staleReason?: UiReasonCategory;
  errorMessage?: string;
}

export interface UiCapabilityStoreSnapshot {
  revision: number;
  contexts: ReadonlyMap<string, UiCapabilityContextRecord>;
}

export interface UiCapabilityDecisionSnapshot {
  contextStatus: UiCapabilityContextStatus | 'MISSING_CONTEXT';
  capability?: UiCapability;
  needsHydration: boolean;
  staleReason?: UiReasonCategory;
  errorMessage?: string;
}

function midpointRefresh(expiresAt: string): string {
  const expires = Date.parse(expiresAt);
  const now = Date.now();
  if (!Number.isFinite(expires) || expires <= now) return new Date(now).toISOString();
  return new Date(now + Math.max(1_000, Math.floor((expires - now) / 2))).toISOString();
}

function actionMap(items: readonly UiCapability[]): ReadonlyMap<string, UiCapability> {
  return new Map(items.map((item) => [item.uiActionId, item]));
}

function sourceMap(items: readonly UiCapability[], source: UiCapabilitySource): ReadonlyMap<string, UiCapabilitySource> {
  return new Map(items.map((item) => [item.uiActionId, source]));
}

function sameOptionalNumber(left?: number, right?: number): boolean {
  return left === undefined || right === undefined || left === right;
}

export class UiCapabilityStore {
  private listeners = new Set<() => void>();
  private snapshot: UiCapabilityStoreSnapshot = { revision: 0, contexts: new Map() };

  constructor(bootstrap?: UiPageBootstrap, resourceId?: string) {
    if (bootstrap && resourceId) this.seedBootstrap(bootstrap, resourceId);
  }

  subscribe = (listener: () => void): (() => void) => {
    this.listeners.add(listener);
    return () => this.listeners.delete(listener);
  };

  getSnapshot = (): UiCapabilityStoreSnapshot => this.snapshot;

  getContext(contextId: string): UiCapabilityContextRecord | undefined {
    return this.snapshot.contexts.get(contextId);
  }

  getDecision(contextId: string, uiActionId: string, now = Date.now()): UiCapabilityDecisionSnapshot {
    const context = this.getContext(contextId);
    if (!context) return { contextStatus: 'MISSING_CONTEXT', needsHydration: false };
    if (context.status === 'STALE') {
      return {
        contextStatus: 'STALE',
        capability: context.capabilities.get(uiActionId),
        needsHydration: false,
        staleReason: context.staleReason ?? 'RESOURCE_CHANGED',
      };
    }
    if (context.status === 'ERROR') {
      return {
        contextStatus: 'ERROR',
        capability: context.capabilities.get(uiActionId),
        needsHydration: false,
        errorMessage: context.errorMessage,
      };
    }
    const capability = context.capabilities.get(uiActionId);
    const refreshAfter = Date.parse(context.identity.refreshAfter);
    const expiresAt = Date.parse(context.identity.expiresAt);
    const expired = !Number.isFinite(expiresAt) || expiresAt <= now;
    const refreshDue = Number.isFinite(refreshAfter) && refreshAfter <= now;
    return {
      contextStatus: context.status,
      capability,
      needsHydration: context.status !== 'HYDRATING' && (!capability || expired || refreshDue),
    };
  }

  seedBootstrap(bootstrap: UiPageBootstrap, resourceId: string): void {
    const contextId = bootstrap.routeContext;
    const existing = this.getContext(contextId);
    const identity: UiCapabilityContextIdentity = {
      contextId,
      resourceId,
      tenantId: bootstrap.tenantId,
      principalEpoch: bootstrap.principalEpoch,
      catalogRevision: bootstrap.catalogRevision,
      policyVersion: bootstrap.policyVersion,
      resourceRefHash: bootstrap.resourceSummaryRef || undefined,
      resourceVersion: bootstrap.resourceVersion,
      expiresAt: bootstrap.expiresAt,
      refreshAfter: midpointRefresh(bootstrap.expiresAt),
    };
    const incompatible = existing && (
      existing.identity.resourceId !== resourceId
      || existing.identity.tenantId !== identity.tenantId
      || existing.identity.principalEpoch !== identity.principalEpoch
      || existing.identity.catalogRevision !== identity.catalogRevision
      || existing.identity.policyVersion !== identity.policyVersion
      || !sameOptionalNumber(existing.identity.resourceVersion, identity.resourceVersion)
    );
    const capabilities = incompatible || !existing
      ? actionMap(bootstrap.pageCapabilities)
      : new Map([...existing.capabilities, ...bootstrap.pageCapabilities.map((item) => [item.uiActionId, item] as const)]);
    const sources = incompatible || !existing
      ? sourceMap(bootstrap.pageCapabilities, 'BOOTSTRAP')
      : new Map([...existing.sourceByAction, ...bootstrap.pageCapabilities.map((item) => [item.uiActionId, 'BOOTSTRAP'] as const)]);
    this.replaceContext(contextId, {
      identity,
      capabilities,
      sourceByAction: sources,
      status: 'READY',
    });
  }

  beginHydration(contextId: string, requestId: string): UiCapabilityContextIdentity | undefined {
    const context = this.getContext(contextId);
    if (!context || context.status === 'STALE') return undefined;
    this.replaceContext(contextId, { ...context, status: 'HYDRATING', hydrationRequestId: requestId, errorMessage: undefined });
    return context.identity;
  }

  acceptEnvelope(envelope: UiCapabilityEnvelope, requestId: string): boolean {
    const context = this.getContext(envelope.contextId);
    if (!context || context.hydrationRequestId !== requestId) return false;
    const identity = context.identity;
    const sameAuthority = envelope.tenantId === identity.tenantId
      && envelope.principalEpoch === identity.principalEpoch
      && envelope.catalogRevision === identity.catalogRevision
      && envelope.policyVersion === identity.policyVersion
      && sameOptionalNumber(envelope.resourceVersion, identity.resourceVersion);
    if (!sameAuthority) {
      this.replaceContext(envelope.contextId, {
        ...context,
        status: 'STALE',
        hydrationRequestId: undefined,
        staleReason: envelope.tenantId !== identity.tenantId || envelope.principalEpoch !== identity.principalEpoch
          ? 'TENANT_CONTEXT_CHANGED'
          : 'RESOURCE_CHANGED',
      });
      return false;
    }
    const capabilities = new Map(context.capabilities);
    const sources = new Map(context.sourceByAction);
    for (const capability of envelope.capabilities) {
      capabilities.set(capability.uiActionId, capability);
      sources.set(capability.uiActionId, 'HYDRATION');
    }
    this.replaceContext(envelope.contextId, {
      identity: {
        ...identity,
        resourceRefHash: envelope.resourceRefHash || identity.resourceRefHash,
        resourceVersion: envelope.resourceVersion ?? identity.resourceVersion,
        expiresAt: envelope.expiresAt,
        refreshAfter: envelope.refreshAfter,
      },
      capabilities,
      sourceByAction: sources,
      status: 'READY',
    });
    return true;
  }

  markHydrationError(contextId: string, requestId: string, message: string): void {
    const context = this.getContext(contextId);
    if (!context || context.hydrationRequestId !== requestId) return;
    this.replaceContext(contextId, {
      ...context,
      status: 'ERROR',
      hydrationRequestId: undefined,
      errorMessage: message,
    });
  }

  invalidateContext(contextId: string, reason: UiReasonCategory = 'RESOURCE_CHANGED'): void {
    const context = this.getContext(contextId);
    if (!context) return;
    this.replaceContext(contextId, {
      ...context,
      status: 'STALE',
      hydrationRequestId: undefined,
      staleReason: reason,
      errorMessage: undefined,
    });
  }

  resetContextForRefresh(contextId: string): void {
    const context = this.getContext(contextId);
    if (!context) return;
    this.replaceContext(contextId, {
      ...context,
      status: 'READY',
      staleReason: undefined,
      errorMessage: undefined,
      identity: { ...context.identity, refreshAfter: new Date(0).toISOString() },
    });
  }

  clear(): void {
    this.snapshot = { revision: this.snapshot.revision + 1, contexts: new Map() };
    this.emit();
  }

  private replaceContext(contextId: string, next: UiCapabilityContextRecord): void {
    const contexts = new Map(this.snapshot.contexts);
    contexts.set(contextId, next);
    this.snapshot = { revision: this.snapshot.revision + 1, contexts };
    this.emit();
  }

  private emit(): void {
    for (const listener of this.listeners) listener();
  }
}
