'use client';

import type { ReactNode } from 'react';
import { usePathname } from 'next/navigation';
import { EntitlementPageGuard } from '@/components/auth/EntitlementPageGuard';
import { settingsFeatureForPath } from '@/lib/navigation/routeFamilyEntitlements';

/**
 * Settings is not one homogeneous entitlement family: Integrations has its own
 * Core feature while governance/runtime settings are projected as administration.
 * The route-aware layout keeps that distinction without duplicating guards across
 * every settings page or inventing frontend authorization.
 */
export function SettingsRouteEntitlementGuard({ children }: Readonly<{ children: ReactNode }>) {
  const pathname = usePathname();
  const featureId = settingsFeatureForPath(pathname);
  return <EntitlementPageGuard featureId={featureId}>{children}</EntitlementPageGuard>;
}
