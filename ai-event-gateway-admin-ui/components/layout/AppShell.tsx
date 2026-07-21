'use client';

import type { ReactNode } from 'react';
import { usePathname, useRouter } from 'next/navigation';
import { useEffect, useState } from 'react';
import { AuthGate } from '@/components/auth/AuthGate';
import { AuthProvider, useAuth } from '@/components/auth/AuthProvider';
import { AdminRealtimeProvider } from '@/components/providers/AdminRealtimeProvider';
import { ToastViewport } from '@/components/common/ToastViewport';
import { Sidebar } from './Sidebar';
import { Topbar } from './Topbar';


const SUPPORT_ONLY_PREFIXES = [
  '/assignment-profiles',
  '/supply-profiles',
  '/dispatch-policies',
  '/settings/dispatch-governance',
  '/testing/dispatch-readiness',
  '/testing/dispatch-simulator',
] as const;

function isSupportOnlyRoute(pathname: string): boolean {
  return SUPPORT_ONLY_PREFIXES.some((prefix) => pathname === prefix || pathname.startsWith(`${prefix}/`));
}

function ProtectedShell({ children }: Readonly<{ children: ReactNode }>) {
  const pathname = usePathname();
  const router = useRouter();
  const { hasRole } = useAuth();
  const [sidebarOpen, setSidebarOpen] = useState(false);
  const supportOnlyRoute = isSupportOnlyRoute(pathname);
  const supportAuthorized = !supportOnlyRoute || hasRole('SUPPORT');

  useEffect(() => {
    setSidebarOpen(false);
  }, [pathname]);

  useEffect(() => {
    if (supportOnlyRoute && !hasRole('SUPPORT')) router.replace('/dashboard');
  }, [hasRole, router, supportOnlyRoute]);

  if (pathname === '/login') {
    return <main className="min-h-screen bg-slate-50 p-6 sm:p-8">{children}</main>;
  }

  if (!supportAuthorized) {
    return (
      <main className="min-h-screen bg-slate-50 p-6 sm:p-8">
        <section className="mx-auto max-w-2xl rounded-2xl border border-amber-200 bg-amber-50 p-6 text-amber-950 shadow-sm">
          <h1 className="text-lg font-black">Legacy mutation API 已停用</h1>
          <p className="mt-2 text-sm leading-6">此歷史相容頁僅供 SUPPORT 角色查閱。介面模式不會授予存取權限，系統將返回 Current 管理首頁。</p>
        </section>
      </main>
    );
  }

  return (
    <AdminRealtimeProvider>
      <div className="min-h-screen bg-slate-50">
        <Sidebar open={sidebarOpen} onClose={() => setSidebarOpen(false)} />
        <div className="min-h-screen lg:pl-72">
          <Topbar onMenuClick={() => setSidebarOpen(true)} />
          <div className="p-4 sm:p-6 lg:p-8">{children}</div>
        </div>
        <ToastViewport />
      </div>
    </AdminRealtimeProvider>
  );
}

export function AppShell({ children }: Readonly<{ children: ReactNode }>) {
  return (
    <AuthProvider>
      <AuthGate>
        <ProtectedShell>{children}</ProtectedShell>
      </AuthGate>
    </AuthProvider>
  );
}
