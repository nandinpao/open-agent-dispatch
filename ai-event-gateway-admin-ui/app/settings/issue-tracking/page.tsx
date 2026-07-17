import { AdminPerspectiveBanner } from '@/components/common/AdminPerspectiveBanner';
import { PageHeader } from '@/components/common/PageHeader';
import { IssueTrackingManagementConsole } from '@/components/issue-tracking/IssueTrackingManagementConsole';

export default function IssueTrackingSettingsPage() {
  return (
    <main className="space-y-5">
      <PageHeader
        title="Issue Tracking / Redmine 管理"
        description="以管理流程檢查 Redmine adapter：設定檢查、連線測試、project/tracker 選擇、測試 issue 與 action queue/result。"
      />
      <AdminPerspectiveBanner />
      <IssueTrackingManagementConsole />
    </main>
  );
}
