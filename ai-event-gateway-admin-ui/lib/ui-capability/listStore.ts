import type {
  UiCapability,
  UiCapabilityEnvelope,
  UiListCapabilitySummary,
  UiReasonCategory,
} from '@/lib/ui-capability/contracts';

export type UiListCapabilityStatus = 'IDLE' | 'QUEUED' | 'HYDRATING' | 'READY' | 'DENIED' | 'STALE' | 'ERROR';

export interface UiListCapabilityIdentity {
  contextId: string;
  resourceId: string;
  resourceVersion?: number;
  generation: number;
}

export interface UiListCapabilityAuthority {
  principalEpoch: number;
  catalogRevision: number;
  policyVersion: number;
}

export interface UiListCapabilityRecord {
  identity: UiListCapabilityIdentity;
  status: UiListCapabilityStatus;
  capabilities: ReadonlyMap<string, UiCapability>;
  authority?: UiListCapabilityAuthority;
  expiresAt?: string;
  activeRequestSequence: number;
  reason?: UiReasonCategory;
  error?: string;
  correlationId?: string;
}

export interface UiListCapabilitySnapshot {
  revision: number;
  contexts: ReadonlyMap<string, UiListCapabilityRecord>;
}

const EMPTY = new Map<string, UiCapability>();

function sameAuthority(left: UiListCapabilityAuthority | undefined, right: UiListCapabilityAuthority): boolean {
  return left === undefined
    || (left.principalEpoch === right.principalEpoch
      && left.catalogRevision === right.catalogRevision
      && left.policyVersion === right.policyVersion);
}

function capabilityMap(capabilities: readonly UiCapability[]): ReadonlyMap<string, UiCapability> {
  const result = new Map<string, UiCapability>();
  for (const capability of capabilities) result.set(capability.uiActionId, capability);
  return result;
}

export function listCapabilityRecordIsFresh(record: UiListCapabilityRecord | undefined, now = Date.now()): boolean {
  if (!record || record.status !== 'READY') return false;
  if (!record.expiresAt) return true;
  const expiresAt = Date.parse(record.expiresAt);
  return Number.isFinite(expiresAt) && expiresAt > now;
}

export class UiListCapabilityStore {
  private snapshot: UiListCapabilitySnapshot = { revision: 0, contexts: new Map() };
  private readonly listeners = new Set<() => void>();

  readonly subscribe = (listener: () => void): (() => void) => {
    this.listeners.add(listener);
    return () => this.listeners.delete(listener);
  };

  readonly getSnapshot = (): UiListCapabilitySnapshot => this.snapshot;

  get(contextId: string): UiListCapabilityRecord | undefined {
    return this.snapshot.contexts.get(contextId);
  }

  register(identity: Omit<UiListCapabilityIdentity, 'generation'>): UiListCapabilityIdentity {
    const current = this.get(identity.contextId);
    const same = current
      && current.identity.resourceId === identity.resourceId
      && current.identity.resourceVersion === identity.resourceVersion;
    const nextIdentity: UiListCapabilityIdentity = {
      ...identity,
      generation: same ? current.identity.generation : (current?.identity.generation ?? 0) + 1,
    };
    if (!same) {
      this.replace(identity.contextId, {
        identity: nextIdentity,
        status: 'IDLE',
        capabilities: EMPTY,
        activeRequestSequence: 0,
      });
    }
    return nextIdentity;
  }

  seedEmbedded(summary: UiListCapabilitySummary, resourceId: string): UiListCapabilityIdentity {
    const identity = this.register({
      contextId: summary.contextId,
      resourceId,
      resourceVersion: summary.resourceVersion,
    });
    const current = this.get(summary.contextId);
    if (!current || current.identity.generation !== identity.generation) return identity;
    this.replace(summary.contextId, {
      ...current,
      status: 'READY',
      capabilities: new Map(Object.entries(summary.actions)),
      authority: {
        principalEpoch: summary.principalEpoch,
        catalogRevision: summary.catalogRevision,
        policyVersion: summary.policyVersion,
      },
      expiresAt: summary.expiresAt,
      error: undefined,
      reason: undefined,
    });
    return identity;
  }

  queue(contextId: string): void {
    const current = this.get(contextId);
    if (!current || current.status === 'STALE') return;
    this.replace(contextId, { ...current, status: 'QUEUED', error: undefined });
  }

  begin(contextId: string, generation: number, correlationId?: string): number | undefined {
    const current = this.get(contextId);
    if (!current || current.identity.generation !== generation || current.status === 'STALE') return undefined;
    const requestSequence = current.activeRequestSequence + 1;
    this.replace(contextId, {
      ...current,
      status: 'HYDRATING',
      activeRequestSequence: requestSequence,
      error: undefined,
      correlationId: correlationId ?? current.correlationId,
    });
    return requestSequence;
  }

  accept(envelope: UiCapabilityEnvelope, generation: number, requestSequence: number): boolean {
    const current = this.get(envelope.contextId);
    if (!current
        || current.identity.generation !== generation
        || current.activeRequestSequence !== requestSequence
        || current.status === 'STALE') return false;
    if (current.identity.resourceVersion !== undefined
        && envelope.resourceVersion !== undefined
        && current.identity.resourceVersion !== envelope.resourceVersion) {
      this.replace(envelope.contextId, { ...current, status: 'STALE', reason: 'RESOURCE_CHANGED' });
      return false;
    }
    const authority: UiListCapabilityAuthority = {
      principalEpoch: envelope.principalEpoch,
      catalogRevision: envelope.catalogRevision,
      policyVersion: envelope.policyVersion,
    };
    if (!sameAuthority(current.authority, authority)) {
      this.replace(envelope.contextId, { ...current, status: 'STALE', reason: 'RESOURCE_CHANGED' });
      return false;
    }
    this.replace(envelope.contextId, {
      ...current,
      identity: {
        ...current.identity,
        resourceVersion: envelope.resourceVersion ?? current.identity.resourceVersion,
      },
      status: 'READY',
      capabilities: capabilityMap(envelope.capabilities),
      authority,
      expiresAt: envelope.expiresAt,
      error: undefined,
      reason: undefined,
    });
    return true;
  }

  fail(contextId: string, generation: number, requestSequence: number, error: string): void {
    const current = this.get(contextId);
    if (!current
        || current.identity.generation !== generation
        || current.activeRequestSequence !== requestSequence) return;
    this.replace(contextId, { ...current, status: 'ERROR', error });
  }

  deny(contextId: string, generation: number, requestSequence: number, reason: UiReasonCategory, error?: string): void {
    const current = this.get(contextId);
    if (!current
        || current.identity.generation !== generation
        || current.activeRequestSequence !== requestSequence) return;
    this.replace(contextId, { ...current, status: 'DENIED', reason, error });
  }

  stale(contextId: string, generation: number, requestSequence: number, reason: UiReasonCategory): void {
    const current = this.get(contextId);
    if (!current
        || current.identity.generation !== generation
        || current.activeRequestSequence !== requestSequence) return;
    this.replace(contextId, { ...current, status: 'STALE', reason });
  }

  remove(contextId: string, generation: number): void {
    const current = this.get(contextId);
    if (!current || current.identity.generation !== generation) return;
    const contexts = new Map(this.snapshot.contexts);
    contexts.delete(contextId);
    this.snapshot = { revision: this.snapshot.revision + 1, contexts };
    this.emit();
  }

  clear(): void {
    this.snapshot = { revision: this.snapshot.revision + 1, contexts: new Map() };
    this.emit();
  }

  private replace(contextId: string, value: UiListCapabilityRecord): void {
    const contexts = new Map(this.snapshot.contexts);
    contexts.set(contextId, value);
    this.snapshot = { revision: this.snapshot.revision + 1, contexts };
    this.emit();
  }

  private emit(): void {
    for (const listener of this.listeners) listener();
  }
}
