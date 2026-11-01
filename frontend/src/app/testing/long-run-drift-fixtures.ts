import { DriftPoint, LongRunDriftResponse } from '../core/api/long-run-drift.models';

// All data here is invented.

export function driftPoint(overrides: Partial<DriftPoint> = {}): DriftPoint {
  return { date: '2026-10-25', driftPct: 3.8, zone: 'good', distanceKm: 28.1, ...overrides };
}

export function longRunDrift(overrides: Partial<LongRunDriftResponse> = {}): LongRunDriftResponse {
  return {
    windowStart: '2026-05-31',
    windowEnd: '2026-10-31',
    thresholds: { goodBelowPct: 5, poorAbovePct: 10 },
    latest: { date: '2026-10-25', driftPct: 3.8, zone: 'good' },
    lastFourAverage: { driftPct: 4.6, zone: 'good', runCount: 4 },
    points: [
      driftPoint({ date: '2026-06-07', driftPct: 7.2, zone: 'watch', distanceKm: 21.1 }),
      driftPoint({ date: '2026-07-12', driftPct: 11.4, zone: 'poor', distanceKm: 24 }),
      driftPoint({ date: '2026-09-20', driftPct: -1.2, zone: 'good', distanceKm: 30 }),
      driftPoint(),
    ],
    excluded: { total: 0, treadmill: 0, tooShort: 0, missingData: 0 },
    pendingCount: 0,
    ...overrides,
  };
}

/** No long runs at all in the window. */
export function emptyLongRunDrift(): LongRunDriftResponse {
  return longRunDrift({ latest: null, lastFourAverage: null, points: [] });
}
