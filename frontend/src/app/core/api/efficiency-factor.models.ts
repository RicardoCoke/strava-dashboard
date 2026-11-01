// The JSON contract of GET /api/efficiency-factor (see docs/features/014-easy-efficiency-factor/specs.md).
// Mirrors backend/src/main/kotlin/app/stravadashboard/api/EfficiencyFactorDtos.kt.

export type EfRunType = 'easy' | 'long';

export interface EfCurrent {
  efficiency: number;
  date: string;
  runCount: number;
}

export interface EfChange {
  percent: number;
  fromDate: string;
  fromEfficiency: number;
  toDate: string;
  toEfficiency: number;
}

export interface EfPoint {
  date: string;
  runType: EfRunType;
  efficiency: number;
  rollingEfficiency: number | null;
  distanceKm: number;
  movingTimeSeconds: number;
  paceSecondsPerKm: number;
  averageHeartRateBpm: number;
}

export interface EfficiencyFactorResponse {
  windowStart: string;
  windowEnd: string;
  rollingDays: number;
  minRunsForRolling: number;
  current: EfCurrent | null;
  change: EfChange | null;
  points: EfPoint[];
  runsPendingDetail: number;
}

function isObject(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null;
}

const RUN_TYPES: readonly unknown[] = ['easy', 'long'];

function isEfPoint(value: unknown): value is EfPoint {
  return (
    isObject(value) &&
    typeof value['date'] === 'string' &&
    RUN_TYPES.includes(value['runType']) &&
    typeof value['efficiency'] === 'number' &&
    (value['rollingEfficiency'] === null || typeof value['rollingEfficiency'] === 'number') &&
    typeof value['distanceKm'] === 'number' &&
    typeof value['paceSecondsPerKm'] === 'number' &&
    typeof value['averageHeartRateBpm'] === 'number'
  );
}

/** A light shape check, so an unexpected 200 body shows the error state instead of breaking the card. */
export function isEfficiencyFactorResponse(value: unknown): value is EfficiencyFactorResponse {
  if (!isObject(value)) {
    return false;
  }
  const { current, change } = value;
  return (
    (current === null ||
      (isObject(current) &&
        typeof current['efficiency'] === 'number' &&
        typeof current['date'] === 'string' &&
        typeof current['runCount'] === 'number')) &&
    (change === null ||
      (isObject(change) &&
        typeof change['percent'] === 'number' &&
        typeof change['fromDate'] === 'string')) &&
    Array.isArray(value['points']) &&
    value['points'].every(isEfPoint) &&
    typeof value['runsPendingDetail'] === 'number'
  );
}
