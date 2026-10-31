import { Vo2maxResponse, WeekVo2max } from '../core/api/vo2max.models';

// Invented VO2max data for frontend tests (today = Sat 2026-10-31).

function addDays(isoDate: string, days: number): string {
  const [year, month, day] = isoDate.split('-').map(Number);
  return new Date(Date.UTC(year, month - 1, day + days)).toISOString().slice(0, 10);
}

/** 27 Monday weeks from 2026-04-27; every third week is empty, the last one is in progress. */
export function vo2maxWeeks(): WeekVo2max[] {
  return Array.from({ length: 27 }, (_, i) => {
    const weekStart = addDays('2026-04-27', i * 7);
    const empty = i % 3 === 1;
    return {
      weekStart,
      weekEnd: addDays(weekStart, 6),
      vo2max: empty ? null : 50 + i * 0.1,
      runCount: empty ? 0 : 3,
      inProgress: i === 26,
    };
  });
}

export function vo2max(overrides: Partial<Vo2maxResponse> = {}): Vo2maxResponse {
  return {
    maxHr: { bpm: 194, source: 'default' },
    current: {
      vo2max: 52.3,
      basis: 'last7Days',
      periodStart: '2026-10-25',
      periodEnd: '2026-10-31',
      runCount: 4,
    },
    predictions: [
      { key: '5k', label: '5K', timeSeconds: 1157, note: null },
      { key: '10k', label: '10K', timeSeconds: 2399, note: null },
      { key: 'half', label: 'Half marathon', timeSeconds: 5311, note: null },
      {
        key: 'marathon',
        label: 'Marathon',
        timeSeconds: 11058,
        note: 'Marathon predictions from VO2max tend to be optimistic.',
      },
    ],
    windowStart: '2026-04-27',
    windowEnd: '2026-11-01',
    weeks: vo2maxWeeks(),
    runsPendingDetail: 0,
    ...overrides,
  };
}

/** No qualifying runs at all. */
export function emptyVo2max(overrides: Partial<Vo2maxResponse> = {}): Vo2maxResponse {
  return vo2max({
    current: null,
    predictions: [],
    weeks: vo2maxWeeks().map((week) => ({ ...week, vo2max: null, runCount: 0 })),
    ...overrides,
  });
}
