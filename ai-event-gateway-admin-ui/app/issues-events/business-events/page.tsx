import { EntitlementPageGuard } from '@/components/auth/EntitlementPageGuard';
import { BusinessEventConsole } from '@/components/issues-events/BusinessEventConsole';

export default function BusinessEventsPage() {
  return <EntitlementPageGuard featureId="business-events"><BusinessEventConsole /></EntitlementPageGuard>;
}
