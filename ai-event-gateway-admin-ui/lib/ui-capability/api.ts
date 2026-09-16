import { coreTenantApiPost } from '@/lib/api/coreClient';
import { UI_CAPABILITY_API_PATHS } from '@/lib/ui-capability/apiPaths';
import {
  UI_CAPABILITY_CONTRACT_VERSION,
  UI_CAPABILITY_MEDIA_TYPE,
  type UiCapabilityBatchRequest,
  type UiCapabilityBatchResponse,
} from '@/lib/ui-capability/contracts';

export function hydrateUiCapabilities(
  contexts: UiCapabilityBatchRequest['contexts'],
  signal?: AbortSignal,
): Promise<UiCapabilityBatchResponse> {
  return coreTenantApiPost<UiCapabilityBatchResponse>(
    UI_CAPABILITY_API_PATHS.capabilityBatch,
    { contractVersion: UI_CAPABILITY_CONTRACT_VERSION, contexts },
    {
      signal,
      headers: { Accept: UI_CAPABILITY_MEDIA_TYPE },
      // The versioned UI capability media type is the authoritative wire envelope.
      // Do not require the generic Core code/message/data wrapper for this endpoint.
      requireStandardEnvelope: false,
    },
  );
}
