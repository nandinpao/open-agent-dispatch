'use client';

import { DualPlaneDashboard } from '@/components/dashboard/DualPlaneDashboard';

/**
 * Backward-compatible export used by app/dashboard/page.tsx.
 * The implementation is now the Phase C three-layer dashboard: Business Truth, Dispatch Truth, Runtime Diagnostics.
 */
export function DashboardSummary() {
  return <DualPlaneDashboard />;
}
