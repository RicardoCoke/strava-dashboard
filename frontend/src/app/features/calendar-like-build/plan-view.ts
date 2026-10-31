import { HttpErrorResponse } from '@angular/common/http';
import {
  BuildProgress,
  PlanWeek,
  TargetPace,
  TrainingPlanResponse,
} from '../../core/api/training-plan.models';
import { formatWeekdayDayMonth } from '../../shared/format/dates';
import { formatTwoDecimals } from '../../shared/format/numbers';
import { formatPace } from '../../shared/format/pace';

export type PlanErrorReason = 'unavailable' | 'invalid' | 'other';

export type PlanState =
  | { kind: 'loading' }
  | { kind: 'notConfigured' }
  | { kind: 'error'; reason: PlanErrorReason; message: string | null }
  | { kind: 'loaded'; data: TrainingPlanResponse };

function stringField(body: unknown, key: string): string | null {
  if (typeof body !== 'object' || body === null) {
    return null;
  }
  const value = (body as Record<string, unknown>)[key];
  return typeof value === 'string' ? value : null;
}

/** A light shape check, so an unexpected 200 body shows the "other" error instead of breaking the section. */
export function isTrainingPlanResponse(body: unknown): body is TrainingPlanResponse {
  if (typeof body !== 'object' || body === null) {
    return false;
  }
  const plan = body as Record<string, unknown>;
  const progress = plan['progress'];
  return (
    typeof progress === 'object' &&
    progress !== null &&
    typeof (progress as Record<string, unknown>)['label'] === 'string' &&
    Array.isArray(plan['weeks']) &&
    Array.isArray(plan['targetPaces'])
  );
}

/** The section state for a failed request, or null for a 401 (the interceptor redirects to /connect). */
export function planErrorState(error: unknown): PlanState | null {
  if (!(error instanceof HttpErrorResponse)) {
    return { kind: 'error', reason: 'other', message: null };
  }
  const code = stringField(error.error, 'error');
  const message = stringField(error.error, 'message');
  if (error.status === 401) {
    return null;
  }
  if (error.status === 404 && code === 'training_plan_not_configured') {
    return { kind: 'notConfigured' };
  }
  if (error.status === 503 && code === 'training_plan_unavailable') {
    return { kind: 'error', reason: 'unavailable', message: null };
  }
  if (error.status === 422 && code === 'training_plan_invalid' && message !== null) {
    return { kind: 'error', reason: 'invalid', message };
  }
  return { kind: 'error', reason: 'other', message: null };
}

export function planErrorText(reason: PlanErrorReason, message: string | null): string {
  switch (reason) {
    case 'unavailable':
      return "Couldn't read your training plan file.";
    case 'invalid':
      return `Your training plan file has a problem: ${message ?? ''}`;
    case 'other':
      return "Couldn't load your training plan.";
  }
}

/** The week row the calendar opens on: the current week, week 1 before the build, the last week after it. */
export function scrollTargetWeekIndex(progress: BuildProgress, weeks: PlanWeek[]): number {
  switch (progress.phase) {
    case 'inProgress':
      return progress.currentWeekIndex ?? 1;
    case 'notStarted':
      return weeks[0]?.index ?? 1;
    case 'finished':
      return weeks.at(-1)?.index ?? 1;
  }
}

export interface PaceRow {
  key: string;
  label: string;
  /** "5:38 /km" or "4:00–4:10 /km". */
  value: string;
  /** "target 4:05", or null when there is no range or no target. */
  target: string | null;
}

export function paceRows(paces: TargetPace[]): PaceRow[] {
  return paces.map((pace) => {
    const hasRange = pace.minSecondsPerKm !== null && pace.maxSecondsPerKm !== null;
    if (hasRange) {
      return {
        key: pace.key,
        label: pace.label,
        value: `${formatPace(pace.minSecondsPerKm ?? 0)}–${formatPace(pace.maxSecondsPerKm ?? 0)} /km`,
        target:
          pace.targetSecondsPerKm === null ? null : `target ${formatPace(pace.targetSecondsPerKm)}`,
      };
    }
    const value =
      pace.targetSecondsPerKm === null ? '' : `${formatPace(pace.targetSecondsPerKm)} /km`;
    return { key: pace.key, label: pace.label, value, target: null };
  });
}

export interface DayCell {
  date: string;
  dateText: string;
  session: string;
  note: string | null;
  minutesText: string;
  kmText: string;
  isRest: boolean;
  raceLabel: string | null;
  isMarathon: boolean;
  isToday: boolean;
  isPast: boolean;
}

export interface WeekRow {
  index: number;
  label: string;
  kmText: string;
  splitText: string;
  isCurrentWeek: boolean;
  days: DayCell[];
}

export function weekRows(weeks: PlanWeek[]): WeekRow[] {
  return weeks.map((week) => ({
    index: week.index,
    label: week.label,
    kmText: `${formatTwoDecimals(week.distanceKm)} km`,
    splitText: `${formatTwoDecimals(week.easyPercent)}% easy · ${formatTwoDecimals(week.subtPercent)}% subT`,
    isCurrentWeek: week.isCurrentWeek,
    days: week.days.map((day) => ({
      date: day.date,
      dateText: formatWeekdayDayMonth(day.date, day.dayOfWeek),
      session: day.session,
      note: day.note,
      minutesText: `${day.totalMinutes} min`,
      kmText: `${formatTwoDecimals(day.distanceKm)} km`,
      isRest: day.isRest,
      raceLabel: day.race?.label ?? null,
      isMarathon: day.race?.isMarathon ?? false,
      isToday: day.isToday,
      isPast: day.isPast,
    })),
  }));
}
