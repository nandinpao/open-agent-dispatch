'use client';

import type { ReactNode } from 'react';
import { useEffect, useState } from 'react';
import { usePathname } from 'next/navigation';
import { useAuth } from '@/components/auth/AuthProvider';
import { getPublicEnv } from '@/lib/constants/env';

function AuthLoadingBox({ message = 'Status...' }: Readonly<{ message?: string }>) {
  return (
    <div className="flex min-h-screen items-center justify-center bg-slate-50 p-8">
      <div className="rounded-2xl border border-slate-200 bg-white p-6 text-sm text-slate-600 shadow-sm">
        {message}
      </div>
    </div>
  );
}

export function AuthGate({ children }: Readonly<{ children: ReactNode }>) {
  const env = getPublicEnv();
  const pathname = usePathname();
  const { status, refreshCurrentUser } = useAuth();
  const [mounted, setMounted] = useState(false);

  useEffect(() => {
    setMounted(true);
  }, []);

  const publicRoute = ['/login', '/setup', '/forgot-password', '/reset-password'].some((route) => pathname === route || pathname.startsWith(`${route}/`));
  if (!env.authEnabled || env.useMock || publicRoute) {
    return <>{children}</>;
  }

  // Avoid hydration mismatch by rendering the exact same protected-route shell
  // before the browser has mounted and before the cookie-session check completes.
  if (!mounted || status === 'CHECKING') {
    return <AuthLoadingBox />;
  }

  if (status === 'SERVICE_UNAVAILABLE') {
    return (
      <div className="flex min-h-screen items-center justify-center bg-slate-50 p-8">
        <div className="max-w-lg rounded-2xl border border-amber-200 bg-white p-6 shadow-sm">
          <h1 className="text-lg font-black text-slate-950">Administration session temporarily unavailable</h1>
          <p className="mt-2 text-sm leading-6 text-slate-600">
            Your browser was not signed out. The Session or authorization service could not complete
            the validation request. Retry after the service is ready.
          </p>
          <button
            type="button"
            onClick={() => void refreshCurrentUser()}
            className="mt-4 rounded-xl bg-slate-950 px-4 py-2 text-sm font-black text-white"
          >
            Retry session validation
          </button>
        </div>
      </div>
    );
  }

  if (status === 'UNAUTHENTICATED') {
    return <AuthLoadingBox message="Redirecting to sign in…" />;
  }

  return <>{children}</>;
}
