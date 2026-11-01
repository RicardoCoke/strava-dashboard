// The JSON contract of GET /api/marathon-pace (see docs/features/012-marathon-pace-volume/specs.md).
// Mirrors backend/src/main/kotlin/app/stravadashboard/api/MarathonPaceDtos.kt.

export interface MpGoal {
  paceSecondsPerKm: number;
  toleranceSecondsPerKm: number;
  fastestSecondsPerKm: number;
  slowestSecondsPerKm: number;
  minLapMeters: number;
}

export interface MpWeek {
  weekIndex: number;
  weekStart: string;
  weekEnd: string;
  label: string;
  mpKm: number;
  isCurrentWeek: boolean;
  isFuture: boolean;
}

export interface MpSummary {
  totalMpKm: number;
  thisWeekStart: string;
  thisWeekEnd: string;
  thisWeekMpKm: number;
  thisWeekTotalKm: number;
  thisWeekSharePercent: number | null;
}

export interface MarathonPaceResponse {
  goal: MpGoal;
  buildStart: string;
  buildEnd: string;
  weeks: MpWeek[];
  summary: MpSummary;
  runsPendingLaps: number;
}

function isObject(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null;
}

/** A light shape check, so an unexpected 200 body shows the error state instead of breaking the card. */
export function isMarathonPaceResponse(value: unknown): value is MarathonPaceResponse {
  if (!isObject(value)) {
    return false;
  }
  const goal = value['goal'];
  const summary = value['summary'];
  return (
    isObject(goal) &&
    typeof goal['paceSecondsPerKm'] === 'number' &&
    typeof goal['fastestSecondsPerKm'] === 'number' &&
    typeof goal['slowestSecondsPerKm'] === 'number' &&
    typeof goal['minLapMeters'] === 'number' &&
    Array.isArray(value['weeks']) &&
    value['weeks'].length > 0 &&
    value['weeks'].every((week) => isObject(week) && typeof week['mpKm'] === 'number') &&
    isObject(summary) &&
    typeof summary['totalMpKm'] === 'number' &&
    typeof summary['thisWeekMpKm'] === 'number' &&
    typeof summary['thisWeekTotalKm'] === 'number' &&
    typeof value['runsPendingLaps'] === 'number'
  );
}
