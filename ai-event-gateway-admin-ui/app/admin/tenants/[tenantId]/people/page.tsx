import { EntitlementPageGuard } from '@/components/auth/EntitlementPageGuard';
import { PeopleWorkspace } from '@/components/access-management/people/PeopleWorkspace';

export default function Page() {
  return <EntitlementPageGuard featureId="access-people"><PeopleWorkspace /></EntitlementPageGuard>;
}
