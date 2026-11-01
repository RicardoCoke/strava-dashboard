import { WellnessDay, WellnessResponse } from '../core/api/wellness.models';

// All data here is invented.

const WINDOW_END = '2026-10-31';
const WINDOW_DAYS = 84;

function isoDaysBefore(end: string, days: number): string {
  const date = new Date(`${end}T00:00:00Z`);
  date.setUTCDate(date.getUTCDate() - days);
  return date.toISOString().slice(0, 10);
}

export function wellnessDay(overrides: Partial<WellnessDay> = {}): WellnessDay {
  return {
    date: WINDOW_END,
    restingHrBpm: 48,
    restingHrBaselineBpm: 47.4,
    hrvMs: 60,
    hrvBaselineMs: 60.2,
    ...overrides,
  };
}

/** 84 days ending Sat 31 Oct; the first 4 days have no data. */
export function wellnessDays(): WellnessDay[] {
  return Array.from({ length: WINDOW_DAYS }, (_, index) => {
    const date = isoDaysBefore(WINDOW_END, WINDOW_DAYS - 1 - index);
    return index < 4
      ? wellnessDay({
          date,
          restingHrBpm: null,
          restingHrBaselineBpm: null,
          hrvMs: null,
          hrvBaselineMs: null,
        })
      : wellnessDay({ date });
  });
}

export function wellness(overrides: Partial<WellnessResponse> = {}): WellnessResponse {
  return {
    windowStart: '2026-08-09',
    windowEnd: WINDOW_END,
    baselineDays: 7,
    daysWithData: 80,
    days: wellnessDays(),
    latest: {
      restingHr: {
        date: WINDOW_END,
        bpm: 53,
        baselineBpm: 47.4,
        deltaBpm: 5.6,
        deltaLabel: '+5.6 bpm vs baseline',
        flagged: true,
      },
      hrv: {
        date: '2026-10-30',
        ms: 58,
        baselineMs: 60.2,
        deltaPercent: -4,
        deltaLabel: '−4% vs baseline',
        flagged: false,
      },
    },
    readiness: { note: 'Resting HR above baseline while load is high', loadHigh: true },
    source: { status: 'ok', lastFetchedAt: '2026-10-31T06:15:00Z' },
    ...overrides,
  };
}

/** No readings in the window yet. */
export function emptyWellness(overrides: Partial<WellnessResponse> = {}): WellnessResponse {
  const empty = wellnessDay({
    restingHrBpm: null,
    restingHrBaselineBpm: null,
    hrvMs: null,
    hrvBaselineMs: null,
  });
  return wellness({
    daysWithData: 0,
    days: wellnessDays().map((day) => ({ ...empty, date: day.date })),
    latest: { restingHr: null, hrv: null },
    readiness: { note: null, loadHigh: null },
    source: { status: 'pending', lastFetchedAt: null },
    ...overrides,
  });
}
