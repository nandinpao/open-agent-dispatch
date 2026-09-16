import { AdminUiModeNotice } from '@/components/common/AdminUiModeNotice';
import { EnforceReleaseCutoverPanel } from '@/components/release-cutover/EnforceReleaseCutoverPanel';

export default function ReleaseCutoverPage() {
  return <main className="space-y-6"><AdminUiModeNotice requiredMode="advanced" title="Advanced administration area" description="Release Cutover is an advanced operational runbook. Phase 6A adds the unified kernel but does not activate Target authority from this page." /><EnforceReleaseCutoverPanel /></main>;
}
