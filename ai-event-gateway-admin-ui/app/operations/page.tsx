import { EntitlementPageGuard } from '@/components/auth/EntitlementPageGuard';
import { PageHeader } from '@/components/common/PageHeader';
import { OperationsWorkspace } from '@/components/phase7f/OperationsWorkspace';
export default function OperationsPage(){return <EntitlementPageGuard featureId="operations"><main className="space-y-5"><PageHeader title="Operations" description="Create governed exports, inspect attachment safety, review background authorization, preview bulk/import impact, and produce sanitized diagnostic evidence."/><OperationsWorkspace/></main></EntitlementPageGuard>}
