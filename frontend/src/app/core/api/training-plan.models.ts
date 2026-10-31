// The JSON contract of GET /api/training-plan (see docs/features/004-calendar-like-build/specs.md).
// Mirrors backend/src/main/kotlin/app/stravadashboard/api/TrainingPlanDtos.kt.

export type BuildPhase = 'notStarted' | 'inProgress' | 'finished';

export type DayOfWeek =
  'MONDAY' | 'TUESDAY' | 'WEDNESDAY' | 'THURSDAY' | 'FRIDAY' | 'SATURDAY' | 'SUNDAY';

export interface BuildProgress {
  phase: BuildPhase;
  percentComplete: number;
  daysPassed: number;
  totalDays: number;
  /** Set only while the build is in progress. */
  currentWeekIndex: number | null;
  weekCount: number;
  /** Set only while the build is in progress. */
  daysToGo: number | null;
  /** Set only before the build starts. */
  daysUntilStart: number | null;
  /** Ready to show, e.g. "66% · week 11 of 16 · 36 days to go". */
  label: string;
}

export interface TargetPace {
  key: string;
  label: string;
  targetSecondsPerKm: number | null;
  /** Both null or both set. */
  minSecondsPerKm: number | null;
  maxSecondsPerKm: number | null;
}

export interface PlanRace {
  label: string;
  distanceKm: number;
  isMarathon: boolean;
}

export interface PlanDay {
  /** ISO local date. */
  date: string;
  dayOfWeek: DayOfWeek;
  /** "" when the plan has no session text for the day. */
  session: string;
  note: string | null;
  totalMinutes: number;
  easyMinutes: number;
  subtMinutes: number;
  distanceKm: number;
  isRest: boolean;
  race: PlanRace | null;
  isToday: boolean;
  /** The day is before today (server time zone). */
  isPast: boolean;
}

export interface PlanWeek {
  index: number;
  label: string;
  startDate: string;
  endDate: string;
  distanceKm: number;
  easyPercent: number;
  subtPercent: number;
  isCurrentWeek: boolean;
  /** Monday to Sunday, always 7 days. */
  days: PlanDay[];
}

export interface TrainingPlanResponse {
  startDate: string;
  endDate: string;
  progress: BuildProgress;
  targetPaces: TargetPace[];
  weeks: PlanWeek[];
}
