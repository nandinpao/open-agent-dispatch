'use client';

import type { ReactNode } from 'react';
import { usePathname } from 'next/navigation';
import { EntitlementPageGuard } from '@/components/auth/EntitlementPageGuard';
import { settingsFeatureForPath } from '@/lib/navigation/routeFamilyEntitlements';

/**
 * Settings is a presentation shell, not one homogeneous authority family.
 * The route is projected to the same Core-owned feature used by Settings Hub
 * visibility and action entitlements. Backend authorization remains authoritative.
 */
export function SettingsRouteEntitlementGuard({ children }: Readonly<{ children: ReactNode }>) {
  const pathname = usePathname();
  return <EntitlementPageGuard featureId={settingsFeatureForPath(pathname)}>{children}</EntitlementPageGuard>;
}
