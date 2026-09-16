'use client';

import {
  createContext,
  useContext,
  useEffect,
  useMemo,
  useState,
  useSyncExternalStore,
  type ReactNode,
} from 'react';
import { ApiError } from '@/lib/api/client';
import type { UiCapability, UiListCapabilitySummary } from '@/lib/ui-capability/contracts';
import { hydrateUiListCapabilities } from '@/lib/ui-capability/listApi';
import {
  UiListCapabilityStore,
  type UiListCapabilityRecord,
} from '@/lib/ui-capability/listStore';

const MAX_CONTEXTS = 50;
const MAX_ACTIONS = 8;
const DEBOUNCE_MS = 75;

export interface UiListRowRegistration {
  contextId: string;
  resourceId: string;
  resourceVersion?: number;
  uiActionIds: readonly string[];
}

interface QueueEntry extends UiListRowRegistration {
  generation: number;
  refCount: number;
}

interface ActiveBatch {
  controller: AbortController;
  contexts: ReadonlySet<string>;
}

interface AcceptedQueueEntry extends QueueEntry {
  requestSequence: number;
}

class UiListCapabilityCoordinator {
  private readonly registrations = new Map<string, QueueEntry>();
  private readonly queued = new Set<string>();
  private readonly active = new Set<ActiveBatch>();
  private timer: ReturnType<typeof setTimeout> | undefined;
  private disposed = false;
  private authorityUnavailable = false;

  constructor(private readonly store: UiListCapabilityStore) {}

  register(input: UiListRowRegistration): () => void {
    const actions = [...new Set(input.uiActionIds)].slice(0, MAX_ACTIONS);
    const identity = this.store.register({
      contextId: input.contextId,
      resourceId: input.resourceId,
      resourceVersion: input.resourceVersion,
    });
    const current = this.registrations.get(input.contextId);
    const mergedActions = [...new Set([...(current?.uiActionIds ?? []), ...actions])].slice(0, MAX_ACTIONS);
    this.registrations.set(input.contextId, {
      ...input,
      uiActionIds: mergedActions,
      generation: identity.generation,
      refCount: (current?.refCount ?? 0) + 1,
    });
    if (this.authorityUnavailable) {
      // Projection authority is intentionally disabled/unavailable. Keep the row fail-closed but do
      // not create a request storm by retrying a product route that cannot currently serve data.
      this.store.queue(input.contextId);
      const requestSequence = this.store.begin(input.contextId, identity.generation, 'ui-capability-authority-unavailable');
      if (requestSequence !== undefined) {
        this.store.fail(input.contextId, identity.generation, requestSequence, 'UI Capability projection authority is unavailable.');
      }
    } else {
      this.queued.add(input.contextId);
      this.store.queue(input.contextId);
      this.schedule();
    }

    return () => {
      const registration = this.registrations.get(input.contextId);
      if (!registration || registration.generation !== identity.generation) return;
      if (registration.refCount > 1) {
        this.registrations.set(input.contextId, { ...registration, refCount: registration.refCount - 1 });
        return;
      }
      this.registrations.delete(input.contextId);
      this.queued.delete(input.contextId);
      this.store.remove(input.contextId, identity.generation);
      for (const batch of this.active) {
        if ([...batch.contexts].every((contextId) => !this.registrations.has(contextId))) {
          batch.controller.abort();
        }
      }
    };
  }

  dispose(): void {
    this.disposed = true;
    if (this.timer) clearTimeout(this.timer);
    for (const batch of this.active) batch.controller.abort();
    this.active.clear();
    this.queued.clear();
    this.registrations.clear();
    this.store.clear();
  }

  private schedule(): void {
    if (this.disposed || this.authorityUnavailable || this.timer) return;
    this.timer = setTimeout(() => void this.flush(), DEBOUNCE_MS);
  }

  private async flush(): Promise<void> {
    this.timer = undefined;
    if (this.disposed || this.authorityUnavailable || this.queued.size === 0) return;

    const contextIds = [...this.queued].slice(0, MAX_CONTEXTS);
    for (const contextId of contextIds) this.queued.delete(contextId);
    if (this.queued.size > 0) this.schedule();

    const registrations = contextIds
      .map((contextId) => this.registrations.get(contextId))
      .filter((value): value is QueueEntry => value !== undefined);
    if (registrations.length === 0) return;

    const controller = new AbortController();
    const correlationId = typeof crypto !== 'undefined' && 'randomUUID' in crypto
      ? `ui-capability-${crypto.randomUUID()}`
      : `ui-capability-${Date.now()}-${Math.random().toString(36).slice(2)}`;
    const activeBatch: ActiveBatch = { controller, contexts: new Set(registrations.map((item) => item.contextId)) };
    this.active.add(activeBatch);

    const accepted = registrations
      .map((item): AcceptedQueueEntry | undefined => {
        const requestSequence = this.store.begin(item.contextId, item.generation, correlationId);
        return requestSequence === undefined ? undefined : { ...item, requestSequence };
      })
      .filter((item): item is AcceptedQueueEntry => item !== undefined);
    if (accepted.length === 0) {
      this.active.delete(activeBatch);
      return;
    }

    try {
      const response = await hydrateUiListCapabilities(
        accepted.map((item) => ({
          contextId: item.contextId,
          resourceId: item.resourceId,
          resourceVersion: item.resourceVersion,
          uiActionIds: item.uiActionIds,
        })),
        controller.signal,
        correlationId,
      );
      const returned = new Set<string>();
      for (const envelope of response.contexts) {
        returned.add(envelope.contextId);
        const registration = this.registrations.get(envelope.contextId);
        const acceptedEntry = accepted.find((item) => item.contextId === envelope.contextId);
        if (registration && acceptedEntry) {
          this.store.accept(envelope, registration.generation, acceptedEntry.requestSequence);
        }
      }
      for (const item of accepted) {
        if (!returned.has(item.contextId) && this.registrations.has(item.contextId)) {
          this.store.fail(item.contextId, item.generation, item.requestSequence, 'Capability response omitted this row.');
        }
      }
    } catch (error) {
      if (controller.signal.aborted) return;
      const apiError = error instanceof ApiError ? error : undefined;
      const projectionUnavailable = apiError?.status === 404
        || (apiError?.code === 'DEPENDENCY_UNAVAILABLE'
          && apiError.message.includes('UI_CAPABILITY_PROJECTION_DISABLED'));
      if (projectionUnavailable) {
        this.authorityUnavailable = true;
        this.queued.clear();
        if (this.timer) {
          clearTimeout(this.timer);
          this.timer = undefined;
        }
      }
      for (const item of accepted) {
        if (!this.registrations.has(item.contextId)) continue;
        if (apiError?.status === 403) this.store.deny(item.contextId, item.generation, item.requestSequence, 'NOT_ALLOWED', apiError.message);
        else if (apiError?.status === 409 || apiError?.status === 412) this.store.stale(item.contextId, item.generation, item.requestSequence, 'RESOURCE_CHANGED');
        else this.store.fail(item.contextId, item.generation, item.requestSequence, error instanceof Error ? error.message : 'Capability hydration failed.');
      }
    } finally {
      this.active.delete(activeBatch);
    }
  }
}

interface Runtime {
  store: UiListCapabilityStore;
  coordinator: UiListCapabilityCoordinator;
}

const Context = createContext<Runtime | null>(null);

export function UiListCapabilityProvider({
  children,
  embeddedSummaries = [],
  resourceIds = {},
}: Readonly<{
  children: ReactNode;
  embeddedSummaries?: readonly UiListCapabilitySummary[];
  resourceIds?: Readonly<Record<string, string>>;
}>) {
  const [runtime] = useState<Runtime>(() => {
    const store = new UiListCapabilityStore();
    for (const summary of embeddedSummaries) {
      const resourceId = resourceIds[summary.contextId];
      if (resourceId) store.seedEmbedded(summary, resourceId);
    }
    return { store, coordinator: new UiListCapabilityCoordinator(store) };
  });
  useEffect(() => () => runtime.coordinator.dispose(), [runtime.coordinator]);
  return <Context.Provider value={runtime}>{children}</Context.Provider>;
}

function useRuntime(): Runtime {
  const value = useContext(Context);
  if (!value) throw new Error('UiListCapabilityProvider is required');
  return value;
}

export function useUiListCapabilityRow(input: UiListRowRegistration): UiListCapabilityRecord | undefined {
  const runtime = useRuntime();
  useSyncExternalStore(runtime.store.subscribe, runtime.store.getSnapshot, runtime.store.getSnapshot);
  const { contextId, resourceId, resourceVersion, uiActionIds } = input;
  const actionKey = useMemo(() => [...new Set(uiActionIds)].sort().join('|'), [uiActionIds]);
  const registration = useMemo<UiListRowRegistration>(() => ({
    contextId,
    resourceId,
    resourceVersion,
    uiActionIds: actionKey ? actionKey.split('|') : [],
  }), [actionKey, contextId, resourceId, resourceVersion]);
  useEffect(() => runtime.coordinator.register(registration), [registration, runtime.coordinator]);
  return runtime.store.get(contextId);
}

export function listCapability(record: UiListCapabilityRecord | undefined, uiActionId: string): UiCapability | undefined {
  return record?.capabilities.get(uiActionId);
}
