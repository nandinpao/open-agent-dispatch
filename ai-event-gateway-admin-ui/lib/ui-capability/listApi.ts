import { coreTenantApiPost } from '@/lib/api/coreClient';
import { UI_CAPABILITY_API_PATHS } from '@/lib/ui-capability/apiPaths';
import {
  UI_CAPABILITY_CONTRACT_VERSION,
  UI_CAPABILITY_MEDIA_TYPE,
  type UiCapabilityBatchRequest,
  type UiCapabilityBatchResponse,
} from '@/lib/ui-capability/contracts';

export function hydrateUiListCapabilities(
  contexts: UiCapabilityBatchRequest['contexts'],
  signal?: AbortSignal,
  correlationId?: string,
): Promise<UiCapabilityBatchResponse> {
  return coreTenantApiPost<UiCapabilityBatchResponse>(
    UI_CAPABILITY_API_PATHS.listCapabilityBatch,
    { contractVersion: UI_CAPABILITY_CONTRACT_VERSION, contexts },
    {
      signal,
      headers: {
        Accept: UI_CAPABILITY_MEDIA_TYPE,
        ...(correlationId ? { 'X-Correlation-Id': correlationId } : {}),
      },
      requireStandardEnvelope: false,
    },
  );
}
