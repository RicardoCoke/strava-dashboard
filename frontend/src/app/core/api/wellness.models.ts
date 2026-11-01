// The JSON contract of GET /api/wellness (see docs/features/015-garmin-resting-hr-hrv/specs.md).
// Mirrors backend/src/main/kotlin/app/stravadashboard/api/WellnessDtos.kt.

export interface WellnessDay {
  date: string;
  restingHrBpm: number | null;
  restingHrBaselineBpm: number | null;
  hrvMs: number | null;
  hrvBaselineMs: number | null;
}

export interface RestingHrLatest {
  date: string;
  bpm: number;
  baselineBpm: number | null;
  deltaBpm: number | null;
  deltaLabel: string | null;
  flagged: boolean;
}

export interface HrvLatest {
  date: string;
  ms: number;
  baselineMs: number | null;
  deltaPercent: number | null;
  deltaLabel: string | null;
  flagged: boolean;
}

export interface WellnessLatest {
  restingHr: RestingHrLatest | null;
  hrv: HrvLatest | null;
}

export interface WellnessReadiness {
  note: string | null;
  loadHigh: boolean | null;
}

export type WellnessSourceStatus = 'ok' | 'pending' | 'rateLimited' | 'unavailable' | 'keyRejected';

export interface WellnessSource {
  status: WellnessSourceStatus;
  lastFetchedAt: string | null;
}

export interface WellnessResponse {
  windowStart: string;
  windowEnd: string;
  baselineDays: number;
  daysWithData: number;
  days: WellnessDay[];
  latest: WellnessLatest;
  readiness: WellnessReadiness;
  source: WellnessSource;
}

const WINDOW_DAYS = 84;

function isObject(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null;
}

/** A light shape check, so an unexpected 200 body shows the error state instead of breaking the card. */
export function isWellnessResponse(value: unknown): value is WellnessResponse {
  return (
    isObject(value) &&
    Array.isArray(value['days']) &&
    value['days'].length === WINDOW_DAYS &&
    isObject(value['latest']) &&
    isObject(value['readiness']) &&
    isObject(value['source']) &&
    typeof value['daysWithData'] === 'number'
  );
}
