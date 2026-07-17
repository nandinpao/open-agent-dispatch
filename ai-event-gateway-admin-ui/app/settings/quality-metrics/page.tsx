import { InformationArchitectureGuide } from '@/components/common/InformationArchitectureGuide';
import { PageHeader } from '@/components/common/PageHeader';
import { QualityMetricsSummaryPanel } from '@/components/quality/QualityMetricsSummaryPanel';
import { AdminUiModeNotice } from '@/components/common/AdminUiModeNotice';

export default function QualityMetricsSettingsPage() {
  return (
    <main className="space-y-6">
      <PageHeader
        title="Quality Requirements"
        description="Maintain quality metrics used by dispatch rules, such as success rate, recent failures, SLA status, and rolling performance windows."
      />
      <AdminUiModeNotice requiredMode="advanced" title="Advanced administration area" description="This page supports source data, migration, release, or readiness management. It is hidden from Basic Mode navigation so normal Agent setup stays simple." />
      <InformationArchitectureGuide activeLayer="advanced" compact />
      <QualityMetricsSummaryPanel
        title="Agent and service quality metrics"
        description="Dispatch rules reference metric names and thresholds. This page provides the measured values behind those requirements."
      />
    </main>
  );
}
