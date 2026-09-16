'use client';

import type { ReactNode } from 'react';
import Link from 'next/link';
import { useCallback, useEffect, useState } from 'react';
import { iamAuthApi } from '@/lib/api/iamAuthApi';
import { ApiError } from '@/lib/api/errors';

type State = 'CHECKING' | 'READY' | 'FAILED';
type Failure = { status?: number; code?: string; message: string; correlationId?: string };

export function CanonicalManagementGate({ children }: Readonly<{ children: ReactNode }>) {
  const [state, setState] = useState<State>('CHECKING');
  const [failure, setFailure] = useState<Failure | null>(null);

  const verify = useCallback(async () => {
    setState('CHECKING');
    setFailure(null);
    try {
      await iamAuthApi.preflight();
      setState('READY');
    } catch (cause) {
      setFailure(cause instanceof ApiError
        ? { status: cause.status, code: cause.code, message: cause.message, correlationId: cause.correlationId }
        : { message: cause instanceof Error ? cause.message : 'The Canonical Session could not be verified.' });
      setState('FAILED');
    }
  }, []);

  useEffect(() => { void verify(); }, [verify]);

  if (state === 'READY') return <>{children}</>;
  if (state === 'CHECKING') {
    return <main className="mx-auto max-w-3xl rounded-3xl border border-slate-200 bg-white p-8 text-center shadow-sm" aria-live="polite"><h1 className="text-xl font-black text-slate-950">Verifying the management session</h1><p className="mt-2 text-sm text-slate-600">Checking the Canonical Session and CSRF context before loading platform governance data.</p></main>;
  }
  return <main className="mx-auto max-w-3xl rounded-3xl border border-rose-200 bg-rose-50 p-7 text-rose-950 shadow-sm" role="alert"><p className="text-xs font-black uppercase tracking-[.18em]">Management session unavailable</p><h1 className="mt-2 text-2xl font-black">Platform governance could not be loaded</h1><p className="mt-3 text-sm leading-6">{failure?.status === 401 ? 'Your session has expired. Sign in again to continue.' : failure?.status === 503 ? 'Your identity was verified, but the authorization service could not complete the request.' : failure?.message}</p>{failure?.code ? <p className="mt-2 text-xs font-bold">Code: {failure.code}</p> : null}{failure?.correlationId ? <p className="mt-1 text-xs">Reference: {failure.correlationId}</p> : null}<div className="mt-5 flex flex-wrap gap-2"><button type="button" onClick={() => { void verify(); }} className="rounded-xl bg-rose-900 px-4 py-2 text-sm font-black text-white">Retry</button>{failure?.status === 401 ? <Link href="/login" className="rounded-xl border border-rose-300 bg-white px-4 py-2 text-sm font-black text-rose-900">Sign in</Link> : null}</div></main>;
}
