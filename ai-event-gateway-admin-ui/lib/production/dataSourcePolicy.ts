import { getPublicEnv } from '@/lib/constants/env';
import type { DataSourceKind } from '@/components/common/DataSourceBadge';

export function isNonLiveDataSource(source: DataSourceKind): boolean {
  return source === 'fallback' || source === 'fixture' || source === 'mock';
}

export function canDisplayDataSource(source: DataSourceKind): boolean {
  const env = getPublicEnv();
  if (source === 'mock') return env.allowMockData;
  if (source === 'fallback' || source === 'fixture') return env.allowFixtureData;
  return true;
}

export function shouldBlockDataSource(source: DataSourceKind): boolean {
  return isNonLiveDataSource(source) && !canDisplayDataSource(source);
}

export function productionGuardSummary(source: DataSourceKind): string {
  if (source === 'mock') return 'Mock data is disabled by NEXT_PUBLIC_ALLOW_MOCK_DATA=false.';
  if (source === 'fixture' || source === 'fallback') return 'Fixture and fallback data are disabled by NEXT_PUBLIC_ALLOW_FIXTURE_DATA=false.';
  return 'Live data can be displayed.';
}
