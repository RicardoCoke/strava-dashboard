// The JSON contract of GET /api/vo2max (see docs/features/008-vo2max-estimator/specs.md).
// Mirrors backend/src/main/kotlin/app/stravadashboard/api/Vo2maxDtos.kt.

export interface MaxHr {
  bpm: number;
  source: 'default' | 'configured';
}

export interface CurrentVo2max {
  vo2max: number;
  basis: 'last7Days' | 'last28Days';
  periodStart: string;
  periodEnd: string;
  runCount: number;
}

export interface RacePrediction {
  key: '5k' | '10k' | 'half' | 'marathon';
  label: string;
  timeSeconds: number;
  note: string | null;
}

export interface WeekVo2max {
  weekStart: string;
  weekEnd: string;
  vo2max: number | null;
  runCount: number;
  inProgress: boolean;
}

export interface Vo2maxResponse {
  maxHr: MaxHr;
  current: CurrentVo2max | null;
  predictions: RacePrediction[];
  windowStart: string;
  windowEnd: string;
  weeks: WeekVo2max[];
  runsPendingDetail: number;
}

function isObject(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null;
}

/** A light shape check, so an unexpected 200 body shows the error state instead of breaking the card. */
export function isVo2maxResponse(value: unknown): value is Vo2maxResponse {
  if (!isObject(value)) {
    return false;
  }
  const current = value['current'];
  return (
    isObject(value['maxHr']) &&
    typeof value['maxHr']['bpm'] === 'number' &&
    (current === null || (isObject(current) && typeof current['vo2max'] === 'number')) &&
    Array.isArray(value['predictions']) &&
    Array.isArray(value['weeks']) &&
    value['weeks'].length > 0 &&
    typeof value['runsPendingDetail'] === 'number'
  );
}
