import { EntitlementPageGuard } from '@/components/auth/EntitlementPageGuard';
import { OrganizationWorkspace } from '@/components/access-management/organization/OrganizationWorkspace';

export default function Page() {
  return <EntitlementPageGuard featureId="access-organization"><OrganizationWorkspace /></EntitlementPageGuard>;
}
