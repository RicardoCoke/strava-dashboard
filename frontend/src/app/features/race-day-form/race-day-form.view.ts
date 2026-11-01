import { HttpErrorResponse } from '@angular/common/http';
import { RaceDayFormResponse, RaceDaySummary } from '../../core/api/race-day-form.models';
import { formatShortDate } from '../../shared/format/dates';

export type MissingSetting = 'plan' | 'lthr' | 'both';

export type RaceDayFormState =
  | { kind: 'loading' }
  | { kind: 'notConfigured'; missing: MissingSetting }
  | { kind: 'invalidConfig'; message: string }
  | { kind: 'error' }
  | { kind: 'loaded'; data: RaceDayFormResponse };

export const NOT_ENOUGH_RUNS_TEXT = 'Not enough runs with heart rate yet to project the plan.';
export const BAND_CAPTION = 'Ready band: form at 5–25% of fitness. A rough guide, not a rule.';
export const SUBT_NOTE =
  'SubT load is an estimate (2× easy): fewer than 3 SubT runs with heart rate in the last 6 months.';

const MISSING_CODES: Record<string, MissingSetting> = {
  race_day_form_missing_plan: 'plan',
  race_day_form_missing_lthr: 'lthr',
  race_day_form_missing_plan_and_lthr: 'both',
};

function stringField(body: unknown, key: string): string | null {
  if (typeof body !== 'object' || body === null) {
    return null;
  }
  const value = (body as Record<string, unknown>)[key];
  return typeof value === 'string' ? value : null;
}

/** The card state for a failed request, or null for a 401 (the interceptor redirects to /connect). */
export function raceDayFormStateFromError(error: unknown): RaceDayFormState | null {
  if (!(error instanceof HttpErrorResponse)) {
    return { kind: 'error' };
  }
  if (error.status === 401) {
    return null;
  }
  const code = stringField(error.error, 'error') ?? '';
  const message = stringField(error.error, 'message');
  if (error.status === 404 && code in MISSING_CODES) {
    return { kind: 'notConfigured', missing: MISSING_CODES[code] };
  }
  if (error.status === 422 && code === 'race_day_form_invalid_config' && message !== null) {
    return { kind: 'invalidConfig', message };
  }
  return { kind: 'error' };
}

/** Which setting is missing and where to set it. Facts only. */
export function notConfiguredText(missing: MissingSetting): string {
  switch (missing) {
    case 'plan':
      return 'Race-day form needs a training plan. Set TRAINING_PLAN_CSV in .env.';
    case 'lthr':
      return 'Race-day form needs a threshold heart rate. Set LTHR_BPM in .env, then restart the backend.';
    case 'both':
      return 'Race-day form needs a training plan and a threshold heart rate. Set TRAINING_PLAN_CSV and LTHR_BPM in .env, then restart the backend.';
  }
}

/** "Race day · Sun 6 Dec · projected", or without "projected" once the race day is actual. */
export function tileCaption(raceDay: RaceDaySummary): string {
  const caption = `Race day · ${formatShortDate(raceDay.date)}`;
  return raceDay.kind === 'planned' ? `${caption} · projected` : caption;
}

export function subtNoteText(data: RaceDayFormResponse): string | null {
  return data.projection === 'projected' && data.subtEstimated ? SUBT_NOTE : null;
}
