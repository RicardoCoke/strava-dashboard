import { HttpErrorResponse } from '@angular/common/http';
import { LoadRamp, TrainingLoadResponse, WeekRamp } from '../../core/api/training-load.models';
import { formatOneDecimal } from '../../shared/format/numbers';

export type LoadState =
  | { kind: 'loading' }
  | { kind: 'notConfigured' }
  | { kind: 'invalidConfig'; message: string }
  | { kind: 'error' }
  | { kind: 'loaded'; data: TrainingLoadResponse };

function stringField(body: unknown, key: string): string | null {
  if (typeof body !== 'object' || body === null) {
    return null;
  }
  const value = (body as Record<string, unknown>)[key];
  return typeof value === 'string' ? value : null;
}

function runs(count: number): string {
  return `${count} ${count === 1 ? 'run' : 'runs'}`;
}

/** The card state for a failed request, or null for a 401 (the interceptor redirects to /connect). */
export function loadStateFromError(error: unknown): LoadState | null {
  if (!(error instanceof HttpErrorResponse)) {
    return { kind: 'error' };
  }
  const code = stringField(error.error, 'error');
  const message = stringField(error.error, 'message');
  if (error.status === 401) {
    return null;
  }
  if (error.status === 404 && code === 'training_load_not_configured') {
    return { kind: 'notConfigured' };
  }
  if (error.status === 422 && code === 'training_load_invalid_config' && message !== null) {
    return { kind: 'invalidConfig', message };
  }
  return { kind: 'error' };
}

function rampText(prefix: string, week: WeekRamp): string {
  const loads = `load ${formatOneDecimal(week.load)} vs ${formatOneDecimal(week.previousLoad)}`;
  return `${prefix}: ${week.changeLabel} (${loads})`;
}

/** Facts-only callouts, one per week with a warning, this week first. */
export function rampWarningTexts(ramp: LoadRamp): string[] {
  return [
    ...(ramp.thisWeek.warning ? [rampText('This week so far', ramp.thisWeek)] : []),
    ...(ramp.lastWeek.warning ? [rampText('Last week', ramp.lastWeek)] : []),
  ];
}

/** Zones line, counts line and, while heart-rate data is importing, a pending line. */
export function footnoteText(data: TrainingLoadResponse): string[] {
  const skipped =
    data.runsSkippedNoHeartRate > 0
      ? ` · ${runs(data.runsSkippedNoHeartRate)} without heart rate skipped`
      : '';
  const pending =
    data.runsPendingHeartRate > 0
      ? [`${runs(data.runsPendingHeartRate)} still importing heart rate; numbers will update.`]
      : [];
  return [
    data.zones.label,
    `Based on ${runs(data.runsCounted)} with heart rate${skipped}`,
    ...pending,
  ];
}

export function isEmpty(data: TrainingLoadResponse): boolean {
  return data.runsCounted === 0;
}

/** The empty-state lines when no run with heart rate is counted yet. */
export function emptyText(data: TrainingLoadResponse): string[] {
  if (data.runsPendingHeartRate > 0) {
    return [
      `Heart-rate data is still importing: ${runs(data.runsPendingHeartRate)} to go. Reload in a while.`,
    ];
  }
  const skipped =
    data.runsSkippedNoHeartRate > 0
      ? [`${runs(data.runsSkippedNoHeartRate)} without heart rate skipped`]
      : [];
  return ['No runs with heart rate in the last 6 months', ...skipped];
}
