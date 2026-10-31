// Mirrors the JSON of GET /api/dashboard/running
// (backend api/RunningDashboardDtos.kt, docs/features/001-running-dashboard/specs.md).

export type SyncStatus = 'ok' | 'partial' | 'rateLimited' | 'stravaUnavailable';

export interface SyncInfo {
  status: SyncStatus;
  /** ISO-8601 UTC instant. */
  lastSyncedAt: string | null;
  /** ISO-8601 UTC instant. */
  retryAfter: string | null;
  runsPendingDetail: number;
}

export interface WeekTotal {
  /** ISO local date (Monday). */
  weekStart: string;
  /** ISO local date (Sunday). */
  weekEnd: string;
  /** e.g. "31 Aug". */
  label: string;
  distanceKm: number;
  runCount: number;
  isCurrentWeek: boolean;
}

export interface WeeklyDistance {
  weeks: WeekTotal[];
}

export interface HrPoint {
  /** ISO local date. */
  date: string;
  averageHeartRateBpm: number;
  distanceKm: number;
  treadmill: boolean;
  /**
   * True when this run is in a stretch where the 3-run average HR is rising (feature 003).
   * The segment leading into it is drawn red-orange.
   */
  rising: boolean;
}

export interface HrTrendLine {
  startDate: string;
  startBpm: number;
  endDate: string;
  endBpm: number;
  changeBpm: number;
}

export interface HrRunType {
  typeKey: string;
  label: string;
  kind: 'easy' | 'long' | 'workout';
  runCount: number;
  runsWithoutHeartRate: number;
  hasEnoughData: boolean;
  points: HrPoint[];
  trend: HrTrendLine | null;
}

export interface HeartRateTrend {
  windowStart: string;
  windowEnd: string;
  minRunsForComparison: number;
  types: HrRunType[];
}

export interface DateWindow {
  start: string;
  end: string;
}

export interface PaceWindowStat {
  averageRepPaceSecondsPerKm: number;
  runCount: number;
}

export interface PacePoint {
  /** ISO local date. */
  date: string;
  repPaceSecondsPerKm: number;
  treadmill: boolean;
}

export interface PaceTypeComparison {
  typeKey: string;
  label: string;
  /** All runs of this type in the 5-month window (feature 010). */
  runCount: number;
  /** One per run with a rep pace, date ascending. */
  points: PacePoint[];
  current: PaceWindowStat | null;
  previous: PaceWindowStat | null;
  /** current − previous; negative means faster. */
  changeSecondsPerKm: number | null;
  runsWithoutPace: number;
  hasEnoughData: boolean;
}

export interface WorkoutPaceComparison {
  /** ISO local date; the same 5-month window as heartRateTrend. */
  windowStart: string;
  windowEnd: string;
  currentWindow: DateWindow;
  previousWindow: DateWindow;
  types: PaceTypeComparison[];
}

// Feature 007: this week vs last week, for the headline tiles.

export type WeekMissing = 'thisWeek' | 'lastWeek' | 'bothWeeks';

export interface DistanceDifference {
  thisWeekKm: number;
  lastWeekKm: number;
  /** This week so far minus all of last week; never −0. */
  differenceKm: number;
}

export interface WeekHrStat {
  averageHeartRateBpm: number;
  runCount: number;
}

export interface EasyHrWeekComparison {
  thisWeek: WeekHrStat | null;
  lastWeek: WeekHrStat | null;
  changeBpm: number | null;
  missingIn: WeekMissing | null;
}

export interface WeekPaceStat {
  averageRepPaceSecondsPerKm: number;
  runCount: number;
  typeLabels: string[];
}

export interface WorkoutPaceWeekComparison {
  thisWeek: WeekPaceStat | null;
  lastWeek: WeekPaceStat | null;
  /** Negative means faster. */
  changeSecondsPerKm: number | null;
  missingIn: WeekMissing | null;
}

export interface WeekComparison {
  thisWeek: DateWindow;
  lastWeek: DateWindow;
  distance: DistanceDifference;
  easyHeartRate: EasyHrWeekComparison;
  workoutPace: WorkoutPaceWeekComparison;
}

export interface RunningDashboardResponse {
  sync: SyncInfo;
  weeklyDistance: WeeklyDistance;
  heartRateTrend: HeartRateTrend;
  workoutPaceComparison: WorkoutPaceComparison;
  weekComparison: WeekComparison;
}

export interface ApiError {
  error: string;
  message: string;
}
