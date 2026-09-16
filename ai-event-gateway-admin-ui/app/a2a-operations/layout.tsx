import type { ReactNode } from 'react';
import { EntitlementPageGuard } from '@/components/auth/EntitlementPageGuard';
import { ROUTE_FAMILY_FEATURES } from '@/lib/navigation/routeFamilyEntitlements';

export default function DelegationsLayout({ children }: Readonly<{ children: ReactNode }>) {
  return <EntitlementPageGuard featureId={ROUTE_FAMILY_FEATURES.delegations}>{children}</EntitlementPageGuard>;
}
