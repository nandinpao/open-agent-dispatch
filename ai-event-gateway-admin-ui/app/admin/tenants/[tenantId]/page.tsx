import { EntitlementPageGuard } from '@/components/auth/EntitlementPageGuard';
import { TenantOverview } from '@/components/access-management/tenants/TenantOverview';
export default function Page() { return <EntitlementPageGuard featureId="access-overview"><TenantOverview /></EntitlementPageGuard>; }
