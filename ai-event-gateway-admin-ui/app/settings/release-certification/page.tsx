import { PageHeader } from '@/components/common/PageHeader';
import { AdminUiModeNotice } from '@/components/common/AdminUiModeNotice';
import { ReleaseCertificationConsole } from '@/components/release-certification/ReleaseCertificationConsole';

export default function ReleaseCertificationPage() {
  return (
    <main className="space-y-5">
      <PageHeader
        title="Release Certification & Cutover"
        description="One governed release workspace: prove runtime safety, prove integrated E2E behavior, certify a release candidate, then promote or roll back explicit Flows. There is no global Dispatch cutover."
      />
      <AdminUiModeNotice
        requiredMode="advanced"
        title="Release engineering control"
        description="This area changes release certification and per-Flow production authority. Everyday runtime and business configuration remain elsewhere."
      />
      <ReleaseCertificationConsole />
    </main>
  );
}
