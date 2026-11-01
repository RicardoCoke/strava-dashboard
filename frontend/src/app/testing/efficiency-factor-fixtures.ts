import { EfPoint, EfficiencyFactorResponse } from '../core/api/efficiency-factor.models';

// All data here is invented.

export function efPoint(overrides: Partial<EfPoint> = {}): EfPoint {
  return {
    date: '2026-10-29',
    runType: 'long',
    efficiency: 1.62,
    rollingEfficiency: 1.652,
    distanceKm: 22.4,
    movingTimeSeconds: 7414,
    paceSecondsPerKm: 331,
    averageHeartRateBpm: 142.2,
    ...overrides,
  };
}

export function efficiencyFactor(
  overrides: Partial<EfficiencyFactorResponse> = {},
): EfficiencyFactorResponse {
  return {
    windowStart: '2026-04-30',
    windowEnd: '2026-10-31',
    rollingDays: 28,
    minRunsForRolling: 3,
    current: { efficiency: 1.652, date: '2026-10-29', runCount: 6 },
    change: {
      percent: 4.2,
      fromDate: '2026-05-03',
      fromEfficiency: 1.585,
      toDate: '2026-10-29',
      toEfficiency: 1.652,
    },
    points: [
      efPoint({ date: '2026-05-01', runType: 'easy', efficiency: 1.57, rollingEfficiency: null }),
      efPoint({ date: '2026-05-03', runType: 'easy', efficiency: 1.598, rollingEfficiency: 1.585 }),
      efPoint({ date: '2026-10-25', runType: 'easy', efficiency: 1.67, rollingEfficiency: 1.66 }),
      efPoint(),
    ],
    runsPendingDetail: 0,
    ...overrides,
  };
}
