import {
  DriftExcluded,
  DriftThresholds,
  DriftZone,
  LongRunDriftResponse,
} from '../../core/api/long-run-drift.models';
import { formatDayMonth } from '../../shared/format/dates';
import { formatOneDecimal } from '../../shared/format/numbers';

export type LongRunDriftState =
  { kind: 'loading' } | { kind: 'error' } | { kind: 'loaded'; data: LongRunDriftResponse };

/** `none`: no long runs at all; `noDrift`: long runs, but none with a drift value yet. */
export type DriftEmptyKind = 'none' | 'noDrift' | null;

export interface HeaderText {
  text: string;
  zone: DriftZone;
}

const MINUS = '−';

/** "+3.8", "−1.2" (U+2212), "0.0". */
export function formatSignedPct(value: number): string {
  const text = formatOneDecimal(Math.abs(value));
  if (text === '0.0') {
    return text;
  }
  return value < 0 ? `${MINUS}${text}` : `+${text}`;
}

export function zoneLabel(zone: DriftZone): string {
  return { good: 'Good', watch: 'Watch', poor: 'Poor' }[zone];
}

export function emptyKind(data: LongRunDriftResponse): DriftEmptyKind {
  if (data.points.length > 0) {
    return null;
  }
  return data.pendingCount > 0 || data.excluded.total > 0 ? 'noDrift' : 'none';
}

/** "Latest: +3.8% · 25 Oct" and "Avg last 4: +4.6%", each with its zone. */
export function headerTexts(data: LongRunDriftResponse): HeaderText[] {
  const texts: HeaderText[] = [];
  if (data.latest) {
    texts.push({
      text: `Latest: ${formatSignedPct(data.latest.driftPct)}% · ${formatDayMonth(data.latest.date)}`,
      zone: data.latest.zone,
    });
  }
  if (data.lastFourAverage) {
    const average = data.lastFourAverage;
    texts.push({
      text: `Avg last ${average.runCount}: ${formatSignedPct(average.driftPct)}%`,
      zone: average.zone,
    });
  }
  return texts;
}

function longRuns(count: number): string {
  return `${count} long ${count === 1 ? 'run' : 'runs'}`;
}

/** "3 long runs left out: 1 treadmill, 2 missing HR or speed data.", or null when none are. */
export function excludedText(excluded: DriftExcluded): string | null {
  if (excluded.total === 0) {
    return null;
  }
  const reasons = [
    [excluded.treadmill, 'treadmill'],
    [excluded.tooShort, 'under 60 min usable'],
    [excluded.missingData, 'missing HR or speed data'],
  ] as const;
  const parts = reasons.filter(([count]) => count > 0).map(([count, why]) => `${count} ${why}`);
  return `${longRuns(excluded.total)} left out: ${parts.join(', ')}.`;
}

/** "2 long runs still syncing", or null when none are. */
export function pendingText(pendingCount: number): string | null {
  return pendingCount > 0 ? `${longRuns(pendingCount)} still syncing` : null;
}

/** "Good < 5% · Watch 5–10% · Poor > 10%". */
export function legendText(thresholds: DriftThresholds): string {
  const good = thresholds.goodBelowPct;
  const poor = thresholds.poorAbovePct;
  return `Good < ${good}% · Watch ${good}–${poor}% · Poor > ${poor}%`;
}
