'use client';
import { WorkspaceError } from '@/components/access-management/shared/workspaceUi';
export default function Error({ error, reset }: Readonly<{ error: Error & { digest?: string }; reset: () => void }>) { return <WorkspaceError message={error.message || 'The Tenant administration route failed.'} correlationId={error.digest} onRetry={reset} />; }
