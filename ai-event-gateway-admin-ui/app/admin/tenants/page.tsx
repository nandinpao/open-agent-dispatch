import { EntitlementPageGuard } from '@/components/auth/EntitlementPageGuard';
import { TenantDirectory } from '@/components/access-management/tenants/TenantDirectory';
export default function Page() { return <EntitlementPageGuard featureId="access-management"><TenantDirectory /></EntitlementPageGuard>; }
