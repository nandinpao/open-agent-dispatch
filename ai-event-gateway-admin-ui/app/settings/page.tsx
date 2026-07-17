import Link from 'next/link';
import { AdminPerspectiveBanner } from '@/components/common/AdminPerspectiveBanner';
import { PageHeader } from '@/components/common/PageHeader';
import { EnvironmentDiagnosticsPanel } from '@/components/settings/EnvironmentDiagnosticsPanel';
import { AuthenticationSecurityPanel } from '@/components/settings/AuthenticationSecurityPanel';
import { ReturnToAgentBanner } from '@/components/common/ReturnToAgentBanner';

type PageProps = {
  searchParams?: Promise<Record<string, string | string[] | undefined>>;
};

const settingsLinks = [
  { href: '/settings/issue-tracking', label: '問題追蹤整合', description: '設定 Redmine、GitLab 等問題追蹤整合。' },
  { href: '/agent-enrollments', label: 'Agent 註冊與核准', description: '審查與核准新 Agent。' },
  { href: '/security-events', label: '安全事件', description: '檢視安全事件與存取異常。' },
];

export default async function SettingsPage({ searchParams }: Readonly<PageProps>) {
  const resolvedSearchParams = await searchParams;
  return (
    <main className="space-y-5">
      <PageHeader title="系統設定" description="設定環境連線、外部整合、安全與權限。派工設定請回到派工流程。" />
      <ReturnToAgentBanner searchParams={resolvedSearchParams} />
      <section className="rounded-3xl border border-slate-200 bg-white p-5 shadow-sm">
        <div className="text-xs font-black uppercase tracking-wide text-slate-500">管理入口</div>
        <h2 className="mt-1 text-lg font-black text-slate-950">一般管理員常用設定</h2>
        <p className="mt-2 text-sm leading-6 text-slate-600">底層維護、資料遷移與原始診斷工具已從一般選單移除，避免與標準 Agent → 派工流程 → Task 流程混淆。</p>
        <div className="mt-4 grid gap-3 md:grid-cols-3">
          {settingsLinks.map((item) => (
            <Link key={item.href} href={item.href} className="rounded-2xl border border-slate-200 bg-slate-50 p-4 hover:border-blue-200 hover:bg-blue-50">
              <div className="font-black text-slate-950">{item.label}</div>
              <p className="mt-2 text-sm leading-6 text-slate-600">{item.description}</p>
            </Link>
          ))}
        </div>
      </section>
      <AdminPerspectiveBanner />
      <AuthenticationSecurityPanel />
      <EnvironmentDiagnosticsPanel />
    </main>
  );
}
