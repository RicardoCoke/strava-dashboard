import { HttpErrorResponse } from '@angular/common/http';
import {
  EfChange,
  EfCurrent,
  EfficiencyFactorResponse,
} from '../../core/api/efficiency-factor.models';
import { formatDayMonth } from '../../shared/format/dates';
import { formatSignedOneDecimal } from '../../shared/format/numbers';
import { Tone, toneForChange } from '../../shared/format/tone';

export type EfficiencyFactorState =
  { kind: 'loading' } | { kind: 'error' } | { kind: 'loaded'; data: EfficiencyFactorResponse };

export const NO_CURRENT_NOTE =
  'Not enough recent runs for a current value (needs 3 in the last 4 weeks)';
export const NO_CHANGE_NOTE = 'Not enough data yet for a change since the start of the window';

function runs(count: number): string {
  return `${count} ${count === 1 ? 'run' : 'runs'}`;
}

/** The card state for a failed request, or null for a 401 (the interceptor redirects to /connect). */
export function efStateFromError(error: unknown): EfficiencyFactorState | null {
  return error instanceof HttpErrorResponse && error.status === 401 ? null : { kind: 'error' };
}

/** "4-week average of 6 runs to 29 Oct". */
export function currentCaption(current: EfCurrent): string {
  return `4-week average of ${runs(current.runCount)} to ${formatDayMonth(current.date)}`;
}

/** "+4.2% since 3 May". */
export function changeText(change: EfChange): string {
  return `${formatSignedOneDecimal(change.percent)}% since ${formatDayMonth(change.fromDate)}`;
}

/** Higher EF is better, so a rise is "better". */
export function changeTone(change: EfChange): Tone {
  return toneForChange(-change.percent);
}

/** The formula line and, while moving time is importing, a pending line. */
export function footnoteText(data: EfficiencyFactorResponse): string[] {
  const formula =
    'EF = average speed (m/min) ÷ average heart rate. Easy and long outdoor runs of 20 min or more ' +
    'with heart rate; treadmill runs are left out.';
  const pending =
    data.runsPendingDetail > 0
      ? [`${runs(data.runsPendingDetail)} still importing moving time; numbers will update.`]
      : [];
  return [formula, ...pending];
}

/** The empty-state text, or null when there is at least one point. */
export function emptyText(data: EfficiencyFactorResponse): string | null {
  if (data.points.length > 0) {
    return null;
  }
  return data.runsPendingDetail > 0
    ? `Moving time is still importing for ${runs(data.runsPendingDetail)}. The chart will appear when it's done.`
    : 'No easy or long runs with heart rate in the last 6 months';
}
