import { HttpErrorResponse } from '@angular/common/http';
import { MarathonPaceResponse, MpGoal, MpSummary } from '../../core/api/marathon-pace.models';
import { formatOneDecimal } from '../../shared/format/numbers';
import { formatPace } from '../../shared/format/pace';

export type MarathonPaceState =
  | { kind: 'loading' }
  | { kind: 'notConfigured' }
  | { kind: 'invalidConfig'; message: string }
  | { kind: 'error' }
  | { kind: 'loaded'; data: MarathonPaceResponse };

export interface SummaryItem {
  label: string;
  value: string;
  caption: string | null;
}

function stringField(body: unknown, key: string): string | null {
  if (typeof body !== 'object' || body === null) {
    return null;
  }
  const value = (body as Record<string, unknown>)[key];
  return typeof value === 'string' ? value : null;
}

/** The card state for a failed request. */
export function stateFromError(error: unknown): MarathonPaceState {
  if (!(error instanceof HttpErrorResponse)) {
    return { kind: 'error' };
  }
  const code = stringField(error.error, 'error');
  const message = stringField(error.error, 'message');
  if (error.status === 404 && code === 'marathon_pace_not_configured') {
    return { kind: 'notConfigured' };
  }
  if (error.status === 422 && code === 'marathon_pace_invalid_config' && message !== null) {
    return { kind: 'invalidConfig', message };
  }
  return { kind: 'error' };
}

/** "Goal 4:15 /km · laps from 4:05 to 4:25 /km, 200 m or longer, outdoor runs". */
export function subtitleText(goal: MpGoal): string {
  const band = `${formatPace(goal.fastestSecondsPerKm)} to ${formatPace(goal.slowestSecondsPerKm)}`;
  return (
    `Goal ${formatPace(goal.paceSecondsPerKm)} /km · laps from ${band} /km, ` +
    `${goal.minLapMeters} m or longer, outdoor runs`
  );
}

export function summaryItems(summary: MpSummary): SummaryItem[] {
  const share = summary.thisWeekSharePercent;
  return [
    { label: 'Total at MP', value: `${formatOneDecimal(summary.totalMpKm)} km`, caption: null },
    {
      label: 'This week at MP',
      value: `${formatOneDecimal(summary.thisWeekMpKm)} km`,
      caption: null,
    },
    {
      label: 'Share of this week',
      value: share === null ? '–' : `${share}%`,
      caption:
        share === null ? 'No runs this week' : `of ${formatOneDecimal(summary.thisWeekTotalKm)} km`,
    },
  ];
}

/** "2 runs still importing laps; numbers will update.", or null when nothing is pending. */
export function pendingText(count: number): string | null {
  if (count <= 0) {
    return null;
  }
  return `${count} ${count === 1 ? 'run' : 'runs'} still importing laps; numbers will update.`;
}

export function isEmpty(data: MarathonPaceResponse): boolean {
  return data.summary.totalMpKm === 0 && data.summary.thisWeekMpKm === 0;
}
