import { EntitlementPageGuard } from '@/components/auth/EntitlementPageGuard';
import { EnforcementActivationConsole } from '@/components/enforcement-activation/EnforcementActivationConsole';
export default function Page(){return <EntitlementPageGuard featureId="enforcement-activation"><EnforcementActivationConsole/></EntitlementPageGuard>;}
