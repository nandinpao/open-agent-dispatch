import type { ReactNode } from 'react';
import { SettingsRouteEntitlementGuard } from '@/components/auth/SettingsRouteEntitlementGuard';

export default function SettingsLayout({ children }: Readonly<{ children: ReactNode }>) {
  return <SettingsRouteEntitlementGuard>{children}</SettingsRouteEntitlementGuard>;
}
