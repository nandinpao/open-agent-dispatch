'use client';

import { RouteState } from '@/components/common/RouteState';

export default function Error({ error, reset }: Readonly<{ error: Error & { digest?: string }; reset: () => void }>) {
  return (
    <RouteState
      title="The Admin Console encountered an error"
      description={error.message || 'An unexpected rendering or data-processing error occurred. Refresh the page or return to Dashboard.'}
      actionLabel="Reload this page"
      onAction={reset}
    />
  );
}
