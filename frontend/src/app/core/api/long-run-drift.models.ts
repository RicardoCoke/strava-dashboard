// The JSON contract of GET /api/long-run-drift (see docs/features/013-long-run-hr-drift/specs.md).
// Mirrors backend/src/main/kotlin/app/stravadashboard/api/LongRunDriftDtos.kt.

export type DriftZone = 'good' | 'watch' | 'poor';

export interface DriftThresholds {
  goodBelowPct: number;
  poorAbovePct: number;
}

export interface LatestDrift {
  date: string;
  driftPct: number;
  zone: DriftZone;
}

export interface DriftAverage {
  driftPct: number;
  zone: DriftZone;
  runCount: number;
}

export interface DriftPoint {
  date: string;
  driftPct: number;
  zone: DriftZone;
  distanceKm: number;
}

export interface DriftExcluded {
  total: number;
  treadmill: number;
  tooShort: number;
  missingData: number;
}

export interface LongRunDriftResponse {
  windowStart: string;
  windowEnd: string;
  thresholds: DriftThresholds;
  latest: LatestDrift | null;
  lastFourAverage: DriftAverage | null;
  points: DriftPoint[];
  excluded: DriftExcluded;
  pendingCount: number;
}

function isObject(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null;
}

const ZONES: readonly unknown[] = ['good', 'watch', 'poor'];

function isDriftPoint(value: unknown): value is DriftPoint {
  return (
    isObject(value) &&
    typeof value['date'] === 'string' &&
    typeof value['driftPct'] === 'number' &&
    ZONES.includes(value['zone']) &&
    typeof value['distanceKm'] === 'number'
  );
}

/** A light shape check, so an unexpected 200 body shows the error state instead of breaking the card. */
export function isLongRunDriftResponse(value: unknown): value is LongRunDriftResponse {
  if (!isObject(value)) {
    return false;
  }
  const { thresholds, excluded, latest, lastFourAverage } = value;
  return (
    isObject(thresholds) &&
    typeof thresholds['goodBelowPct'] === 'number' &&
    typeof thresholds['poorAbovePct'] === 'number' &&
    (latest === null || isObject(latest)) &&
    (lastFourAverage === null || isObject(lastFourAverage)) &&
    Array.isArray(value['points']) &&
    value['points'].every(isDriftPoint) &&
    isObject(excluded) &&
    typeof excluded['total'] === 'number' &&
    typeof value['pendingCount'] === 'number'
  );
}
