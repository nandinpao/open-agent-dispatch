import type { ClusterNodeMetrics } from '@/lib/types/admin';

function timestampMs(metrics?: ClusterNodeMetrics): number | undefined {
  if (!metrics?.timestamp) return undefined;
  const parsed = new Date(metrics.timestamp).getTime();
  return Number.isFinite(parsed) ? parsed : undefined;
}

export function selectFreshestClusterMetrics(
  base: ClusterNodeMetrics,
  candidate?: ClusterNodeMetrics
): ClusterNodeMetrics {
  if (!candidate) return base;

  const baseTimestamp = timestampMs(base);
  const candidateTimestamp = timestampMs(candidate);

  if (candidateTimestamp !== undefined && (baseTimestamp === undefined || candidateTimestamp >= baseTimestamp)) {
    return candidate;
  }

  return base;
}

export function getMemoryUsedPercent(metrics: ClusterNodeMetrics): number {
  if (typeof metrics.memoryUsedPercent === 'number' && Number.isFinite(metrics.memoryUsedPercent)) {
    return Math.round(metrics.memoryUsedPercent * 10) / 10;
  }

  if (metrics.memoryMaxMb <= 0) return 0;
  return Math.round((metrics.memoryUsedMb / metrics.memoryMaxMb) * 1000) / 10;
}
