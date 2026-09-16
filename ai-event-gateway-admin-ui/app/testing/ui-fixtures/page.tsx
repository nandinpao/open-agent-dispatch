import { EntitlementPageGuard } from '@/components/auth/EntitlementPageGuard';
import { SharedUiFixtureClient } from '@/components/testing/SharedUiFixtureClient';

export default function SharedUiFixturePage() {
  return <EntitlementPageGuard featureId="engineering-tools"><SharedUiFixtureClient /></EntitlementPageGuard>;
}
