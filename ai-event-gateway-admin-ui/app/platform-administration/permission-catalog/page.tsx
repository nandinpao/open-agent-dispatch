import { EntitlementPageGuard } from '@/components/auth/EntitlementPageGuard';
import { PermissionCatalogAdministrationConsole } from '@/components/iam/PermissionCatalogAdministrationConsole';

export default function Page() {
  return <EntitlementPageGuard featureId="permission-catalog"><PermissionCatalogAdministrationConsole /></EntitlementPageGuard>;
}
