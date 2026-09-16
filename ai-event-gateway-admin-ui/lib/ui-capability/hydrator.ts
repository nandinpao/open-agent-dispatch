import { ApiError } from '@/lib/api/client';
import { hydrateUiCapabilities } from '@/lib/ui-capability/api';
import type { UiCapabilityContextRequest } from '@/lib/ui-capability/contracts';
import { UiCapabilityStore } from '@/lib/ui-capability/store';

const BATCH_DELAY_MS = 35;
const MAX_CONTEXTS = 20;
const MAX_ACTIONS_PER_CONTEXT = 20;

function requestId(): string {
  return `ui-capability-${Date.now()}-${Math.random().toString(36).slice(2)}`;
}

function invalidationReason(error: ApiError): 'NOT_ALLOWED' | 'RESOURCE_CHANGED' {
  return error.status === 403 ? 'NOT_ALLOWED' : 'RESOURCE_CHANGED';
}

export class UiCapabilityHydrator {
  private queued = new Map<string, Set<string>>();
  private timer: ReturnType<typeof setTimeout> | undefined;
  private activeControllers = new Set<AbortController>();
  private disposed = false;

  constructor(private readonly store: UiCapabilityStore) {}

  request(contextId: string, uiActionIds: readonly string[]): void {
    if (this.disposed) return;
    const context = this.store.getContext(contextId);
    if (!context || context.status === 'STALE') return;
    const actions = this.queued.get(contextId) ?? new Set<string>();
    for (const actionId of uiActionIds) {
      if (actions.size >= MAX_ACTIONS_PER_CONTEXT) break;
      actions.add(actionId);
    }
    this.queued.set(contextId, actions);
    if (!this.timer) this.timer = setTimeout(() => void this.flush(), BATCH_DELAY_MS);
  }

  refreshContext(contextId: string, uiActionIds?: readonly string[]): void {
    const context = this.store.getContext(contextId);
    if (!context) return;
    this.store.resetContextForRefresh(contextId);
    const actions = uiActionIds?.length ? uiActionIds : [...context.capabilities.keys()];
    if (actions.length) this.request(contextId, actions);
  }

  invalidateForMutationStatus(contextId: string, status?: number): void {
    if (status === 403 || status === 409 || status === 412) {
      this.store.invalidateContext(contextId, status === 403 ? 'NOT_ALLOWED' : 'RESOURCE_CHANGED');
    }
  }

  dispose(): void {
    this.disposed = true;
    if (this.timer) clearTimeout(this.timer);
    this.timer = undefined;
    for (const controller of this.activeControllers) controller.abort();
    this.activeControllers.clear();
    this.queued.clear();
  }

  private async flush(): Promise<void> {
    this.timer = undefined;
    if (this.disposed || this.queued.size === 0) return;
    const batch = [...this.queued.entries()].slice(0, MAX_CONTEXTS);
    for (const [contextId] of batch) this.queued.delete(contextId);
    if (this.queued.size > 0 && !this.timer) this.timer = setTimeout(() => void this.flush(), BATCH_DELAY_MS);

    const controller = new AbortController();
    this.activeControllers.add(controller);
    const ids = new Map<string, string>();
    const contexts: UiCapabilityContextRequest[] = [];
    for (const [contextId, actions] of batch) {
      const id = requestId();
      const identity = this.store.beginHydration(contextId, id);
      if (!identity) continue;
      ids.set(contextId, id);
      contexts.push({
        contextId,
        resourceId: identity.resourceId,
        resourceVersion: identity.resourceVersion,
        presentedPrincipalEpoch: identity.principalEpoch,
        uiActionIds: [...actions],
      });
    }
    if (contexts.length === 0) {
      this.activeControllers.delete(controller);
      return;
    }

    try {
      const response = await hydrateUiCapabilities(contexts, controller.signal);
      const returned = new Set<string>();
      for (const envelope of response.contexts) {
        returned.add(envelope.contextId);
        const id = ids.get(envelope.contextId);
        if (id) this.store.acceptEnvelope(envelope, id);
      }
      for (const context of contexts) {
        if (!returned.has(context.contextId)) {
          this.store.markHydrationError(context.contextId, ids.get(context.contextId) ?? '', 'Capability response omitted this context.');
        }
      }
    } catch (error) {
      if (controller.signal.aborted) return;
      const apiError = error instanceof ApiError ? error : undefined;
      for (const context of contexts) {
        const id = ids.get(context.contextId) ?? '';
        if (apiError && (apiError.status === 403 || apiError.status === 409 || apiError.status === 412)) {
          this.store.invalidateContext(context.contextId, invalidationReason(apiError));
        } else {
          this.store.markHydrationError(context.contextId, id, error instanceof Error ? error.message : 'Capability hydration failed.');
        }
      }
    } finally {
      this.activeControllers.delete(controller);
    }
  }
}
