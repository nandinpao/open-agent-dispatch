import { EntitlementPageGuard } from '@/components/auth/EntitlementPageGuard';
import { PermissionReadinessConsole } from '@/components/iam/PermissionReadinessConsole';
export default function Page(){return <EntitlementPageGuard featureId="permission-readiness"><PermissionReadinessConsole/></EntitlementPageGuard>;}
