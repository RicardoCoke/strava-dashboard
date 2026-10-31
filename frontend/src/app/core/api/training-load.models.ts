// The JSON contract of GET /api/training-load (see docs/features/005-training-load/specs.md).
// Mirrors backend/src/main/kotlin/app/stravadashboard/api/TrainingLoadDtos.kt.

export interface LoadZones {
  lthrBpm: number;
  easyBelowBpm: number;
  hardAboveBpm: number;
  label: string;
}

export interface LoadLatest {
  date: string;
  fitness: number;
  fatigue: number;
  form: number;
}

export interface LoadDay {
  date: string;
  load: number;
  fitness: number;
  fatigue: number;
  form: number;
}

export interface WeekRamp {
  weekStart: string;
  weekEnd: string;
  weekToDate: boolean;
  load: number;
  previousLoad: number;
  changePercent: number | null;
  previousWeekLight: boolean;
  warning: boolean;
  changeLabel: string | null;
}

export interface LoadRamp {
  thisWeek: WeekRamp;
  lastWeek: WeekRamp;
}

export interface TrainingLoadResponse {
  windowStart: string;
  windowEnd: string;
  fitnessDays: number;
  fatigueDays: number;
  zones: LoadZones;
  latest: LoadLatest;
  days: LoadDay[];
  ramp: LoadRamp;
  runsCounted: number;
  runsSkippedNoHeartRate: number;
  runsPendingHeartRate: number;
}

function isObject(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null;
}

/** A light shape check, so an unexpected 200 body shows the error state instead of breaking the card. */
export function isTrainingLoadResponse(value: unknown): value is TrainingLoadResponse {
  if (!isObject(value)) {
    return false;
  }
  const ramp = value['ramp'];
  return (
    Array.isArray(value['days']) &&
    value['days'].length > 0 &&
    isObject(value['latest']) &&
    isObject(value['zones']) &&
    typeof value['zones']['label'] === 'string' &&
    isObject(ramp) &&
    isObject(ramp['thisWeek']) &&
    isObject(ramp['lastWeek']) &&
    typeof value['runsCounted'] === 'number' &&
    typeof value['runsSkippedNoHeartRate'] === 'number' &&
    typeof value['runsPendingHeartRate'] === 'number'
  );
}
