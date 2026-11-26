// GET /api/workout-compare/runs and GET /api/workout-compare (docs/features/025-workout-compare/specs.md).
// Mirrors backend/src/main/kotlin/app/stravadashboard/api/WorkoutCompareDtos.kt.

export type CompareFilter = 'all' | 'workouts' | 'long' | 'easy';

export interface CompareRunItem {
  id: number;
  date: string;
  name: string;
  typeKey: string;
  distanceKm: number;
  averagePaceSecondsPerKm: number | null;
  treadmill: boolean;
}

export interface CompareRunsResponse {
  runs: CompareRunItem[];
}

export type CoarseReason =
  | 'notConfigured'
  | 'beforeGarmin'
  | 'treadmill'
  | 'noDetailFile'
  | 'callLimit'
  | 'rateLimited'
  | 'garminUnavailable';

export interface CoarseInfo {
  reason: CoarseReason;
  runId: number | null;
  retryAfter: string | null;
}

export interface ComparePoint {
  distanceKm: number;
  timeSeconds: number;
  paceSecondsPerKm: number | null;
  heartRateBpm: number | null;
}

export interface CompareStats {
  distanceKm: number;
  movingTimeSeconds: number | null;
  averagePaceSecondsPerKm: number | null;
  averageHeartRateBpm: number | null;
  elevationGainMeters: number | null;
  efficiencyFactor: number | null;
  hrDriftPct: number | null;
}

export interface CompareRun {
  id: number;
  date: string;
  name: string;
  typeKey: string;
  treadmill: boolean;
  stats: CompareStats;
  points: ComparePoint[];
}

export interface RepCell {
  paceSecondsPerKm: number;
  heartRateBpm: number | null;
}

/** Differences are later − earlier: negative means the later run was faster or had a lower HR. */
export interface RepRow {
  earlier: RepCell | null;
  later: RepCell | null;
  paceDiffSecondsPerKm: number | null;
  heartRateDiffBpm: number | null;
}

export interface RepComparison {
  mode: 'reps' | 'pieces' | 'unavailable';
  unavailableReason: 'noDetailedData' | 'repsNotFound' | null;
  unavailableRunId: number | null;
  pieceMinutes: number | null;
  piecesRunId: number | null;
  average: RepRow | null;
  pairs: RepRow[];
}

export interface WorkoutCompareResponse {
  resolution: 'stream' | 'splits';
  coarse: CoarseInfo | null;
  runs: [CompareRun, CompareRun];
  reps: RepComparison | null;
}

function isObject(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null;
}

/** Light shape checks, so an unexpected 200 body shows the error state instead of a broken page. */
export function isCompareRunsResponse(value: unknown): value is CompareRunsResponse {
  const runs = isObject(value) ? value['runs'] : null;
  return (
    Array.isArray(runs) &&
    runs.every(
      (run) => isObject(run) && typeof run['id'] === 'number' && typeof run['date'] === 'string',
    )
  );
}

export function isWorkoutCompareResponse(value: unknown): value is WorkoutCompareResponse {
  const runs = isObject(value) ? value['runs'] : null;
  return (
    isObject(value) &&
    typeof value['resolution'] === 'string' &&
    Array.isArray(runs) &&
    runs.length === 2 &&
    runs.every((run) => isObject(run) && isObject(run['stats']) && Array.isArray(run['points']))
  );
}
