import { PageHeader } from '@/components/common/PageHeader';
import { SecurityEventTable } from '@/components/security/SecurityEventTable';

export default function SecurityEventsPage() {
  return (
    <main>
      <PageHeader title="Security Events" description="Core 權威的 Agent 安全事件與稽核紀錄，包含授權拒絕、憑證撤銷後重連、未知 Agent 連入等事件。" />
      <SecurityEventTable />
    </main>
  );
}
