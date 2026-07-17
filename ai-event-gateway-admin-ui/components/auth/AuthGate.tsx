'use client';

import type { ReactNode } from 'react';
import { useEffect, useState } from 'react';
import { usePathname } from 'next/navigation';
import { useAuth } from '@/components/auth/AuthProvider';
import { getPublicEnv } from '@/lib/constants/env';

function AuthLoadingBox({ message = '正在檢查登入狀態...' }: Readonly<{ message?: string }>) {
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
  const { status } = useAuth();
  const [mounted, setMounted] = useState(false);

  useEffect(() => {
    setMounted(true);
  }, []);

  if (!env.authEnabled || env.useMock || pathname === '/login') {
    return <>{children}</>;
  }

  // Avoid hydration mismatch by rendering the exact same protected-route shell
  // before the browser has mounted and before the cookie-session check completes.
  if (!mounted || status === 'CHECKING') {
    return <AuthLoadingBox />;
  }

  if (status === 'UNAUTHENTICATED') {
    return <AuthLoadingBox message="尚未登入，正在導向登入頁..." />;
  }

  return <>{children}</>;
}
