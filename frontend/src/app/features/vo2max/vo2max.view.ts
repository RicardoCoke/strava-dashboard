import { HttpErrorResponse } from '@angular/common/http';
import { NormalizedVo2maxResponse } from '../../core/api/normalized-vo2max.models';
import { CurrentVo2max, Vo2maxResponse } from '../../core/api/vo2max.models';
import { formatDayMonth } from '../../shared/format/dates';

export type Vo2maxState =
  | { kind: 'loading' }
  | { kind: 'invalidConfig'; message: string }
  | { kind: 'error' }
  | { kind: 'loaded'; data: Vo2maxResponse };

export type EmptyKind = 'none' | 'importing' | 'noCurrent';

function stringField(body: unknown, key: string): string | null {
  if (typeof body !== 'object' || body === null) {
    return null;
  }
  const value = (body as Record<string, unknown>)[key];
  return typeof value === 'string' ? value : null;
}

export function runs(count: number): string {
  return `${count} ${count === 1 ? 'run' : 'runs'}`;
}

/** The card state for a failed request, or null for a 401 (the interceptor redirects to /connect). */
export function vo2maxStateFromError(error: unknown): Vo2maxState | null {
  if (!(error instanceof HttpErrorResponse)) {
    return { kind: 'error' };
  }
  if (error.status === 401) {
    return null;
  }
  const message = stringField(error.error, 'message');
  if (
    error.status === 422 &&
    stringField(error.error, 'error') === 'vo2max_invalid_config' &&
    message !== null
  ) {
    return { kind: 'invalidConfig', message };
  }
  return { kind: 'error' };
}

/** "Based on 4 runs in the last 7 days (25 Oct – 31 Oct)". */
export function basisCaption(current: CurrentVo2max): string {
  const period = current.basis === 'last7Days' ? 'the last 7 days' : 'the last 4 weeks';
  const range = `${formatDayMonth(current.periodStart)} – ${formatDayMonth(current.periodEnd)}`;
  return `Based on ${runs(current.runCount)} in ${period} (${range})`;
}

/** Max HR line and, while lap heart rate is importing, a pending line. */
export function footnoteText(data: Vo2maxResponse): string[] {
  const maxHr =
    data.maxHr.source === 'default'
      ? `Max HR ${data.maxHr.bpm} bpm (estimate; set MAX_HR_BPM to change)`
      : `Max HR ${data.maxHr.bpm} bpm (from MAX_HR_BPM)`;
  const pending =
    data.runsPendingDetail > 0
      ? [`${runs(data.runsPendingDetail)} still importing lap heart rate; numbers will update.`]
      : [];
  return [maxHr, ...pending];
}

/** Which empty state applies, or null for the normal view. */
export function emptyKind(data: Vo2maxResponse): EmptyKind | null {
  if (data.current !== null) {
    return null;
  }
  if (data.weeks.some((week) => week.vo2max !== null)) {
    return 'noCurrent';
  }
  return data.runsPendingDetail > 0 ? 'importing' : 'none';
}

/** The "Normalized Vo2" tab's state; `idle` until the tab is first opened (feature 016). */
export type NormalizedVo2maxState =
  | { kind: 'idle' }
  | { kind: 'loading' }
  | { kind: 'invalidConfig'; message: string }
  | { kind: 'error' }
  | { kind: 'loaded'; data: NormalizedVo2maxResponse };

export const NORMALIZED_NOTE =
  'Hill- and drift-adjusted: grade-adjusted pace, only the first 60 minutes of easy and long runs, and each run counts once.';

/** As [vo2maxStateFromError], for the normalized tab: null for a 401 (the interceptor redirects). */
export function normalizedStateFromError(error: unknown): NormalizedVo2maxState | null {
  const state = vo2maxStateFromError(error);
  if (state === null) {
    return null;
  }
  return state.kind === 'invalidConfig' ? state : { kind: 'error' };
}

/** Max HR line and, while split data is importing, a pending line. */
export function normalizedFootnoteText(data: NormalizedVo2maxResponse): string[] {
  const [maxHr] = footnoteText({ ...data, runsPendingDetail: 0 });
  const pending =
    data.runsPendingDetail > 0
      ? [`${runs(data.runsPendingDetail)} still importing split data; numbers will update.`]
      : [];
  return [maxHr, ...pending];
}
