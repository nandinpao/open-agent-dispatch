import { EntitlementPageGuard } from '@/components/auth/EntitlementPageGuard';
import { SourceSystemConsole } from '@/components/source-systems/SourceSystemConsole';

export default function SourceSystemsPage() {
  return <EntitlementPageGuard featureId="source-systems"><SourceSystemConsole /></EntitlementPageGuard>;
}
