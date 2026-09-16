import { PageHeader } from '@/components/common/PageHeader';
import { ProductionFoundationReleaseConsole } from '@/components/release/ProductionFoundationReleaseConsole';

export default function ProductionFoundationPage() {
  return <main className="space-y-5"><PageHeader title="Production Foundation Release" description="Stage 10 A0 Release Gate: certify live evidence, activate one release candidate, and promote only explicit Flows. No global cutover exists."/><ProductionFoundationReleaseConsole/></main>;
}
