'use client';

import type { ReactNode } from 'react';
import { usePathname } from 'next/navigation';
import { useEffect, useState } from 'react';
import { AuthGate } from '@/components/auth/AuthGate';
import { AuthProvider } from '@/components/auth/AuthProvider';
import { AdminRealtimeProvider } from '@/components/providers/AdminRealtimeProvider';
import { RuntimeCapabilityProvider } from '@/components/providers/RuntimeCapabilityProvider';
import { ToastViewport } from '@/components/common/ToastViewport';
import { UiEntitlementProvider } from '@/lib/navigation/useUiEntitlements';
import { Sidebar } from './Sidebar';
import { Topbar } from './Topbar';

const CAPABILITY_INDEPENDENT_ROUTES = ['/login', '/setup', '/forgot-password', '/reset-password', '/change-password'] as const;

function isCapabilityIndependentRoute(pathname: string): boolean {
  return CAPABILITY_INDEPENDENT_ROUTES.some((route) => pathname === route || pathname.startsWith(`${route}/`));
}

function ProtectedShell({ children }: Readonly<{ children: ReactNode }>) {
  const pathname = usePathname();
  const [sidebarOpen, setSidebarOpen] = useState(false);

  useEffect(() => {
    setSidebarOpen(false);
  }, [pathname]);

  if (isCapabilityIndependentRoute(pathname)) {
    return <main className="min-h-screen bg-slate-50 p-6 sm:p-8">{children}</main>;
  }

  return (
    <UiEntitlementProvider>
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
    </UiEntitlementProvider>
  );
}

export function AppShell({ children }: Readonly<{ children: ReactNode }>) {
  return (
    <RuntimeCapabilityProvider>
      <AuthProvider>
        <AuthGate>
          <ProtectedShell>{children}</ProtectedShell>
        </AuthGate>
      </AuthProvider>
    </RuntimeCapabilityProvider>
  );
}
