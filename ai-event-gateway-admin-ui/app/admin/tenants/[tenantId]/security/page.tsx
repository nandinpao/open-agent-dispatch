import { EntitlementPageGuard } from '@/components/auth/EntitlementPageGuard';
import { SecurityWorkspace } from '@/components/access-management/security/SecurityWorkspace';
export default function Page(){return <EntitlementPageGuard featureId="access-security"><SecurityWorkspace/></EntitlementPageGuard>;}
