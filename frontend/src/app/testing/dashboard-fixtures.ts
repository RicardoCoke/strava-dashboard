import { TestBed } from '@angular/core/testing';
import {
  HrRunType,
  PaceTypeComparison,
  RunningDashboardResponse,
  WeekTotal,
} from '../core/api/running-dashboard.models';
import { ChartComponent } from '../shared/chart/chart';

// Invented data for frontend tests.

/** ECharts can't render in jsdom, so specs swap the chart wrapper for an inert element. */
export function stubCharts(): void {
  TestBed.overrideComponent(ChartComponent, {
    set: { imports: [], template: '<div class="chart-stub" [attr.aria-label]="label()"></div>' },
  });
}

export function week(overrides: Partial<WeekTotal> = {}): WeekTotal {
  return {
    weekStart: '2026-08-31',
    weekEnd: '2026-09-06',
    label: '31 Aug',
    distanceKm: 32.4,
    runCount: 4,
    isCurrentWeek: false,
    ...overrides,
  };
}

export function hrType(overrides: Partial<HrRunType> = {}): HrRunType {
  return {
    typeKey: 'easy',
    label: 'Easy runs',
    kind: 'easy',
    runCount: 3,
    runsWithoutHeartRate: 0,
    hasEnoughData: true,
    points: [
      {
        date: '2026-06-01',
        averageHeartRateBpm: 150,
        distanceKm: 8.2,
        treadmill: false,
        rising: false,
      },
      {
        date: '2026-07-01',
        averageHeartRateBpm: 146,
        distanceKm: 8.0,
        treadmill: true,
        rising: false,
      },
      {
        date: '2026-08-01',
        averageHeartRateBpm: 142,
        distanceKm: 8.4,
        treadmill: false,
        rising: false,
      },
    ],
    trend: {
      startDate: '2026-06-01',
      startBpm: 150,
      endDate: '2026-08-01',
      endBpm: 142,
      changeBpm: -8,
    },
    ...overrides,
  };
}

/** Feature 006: a "Long runs" entry; the second point is a treadmill run. */
export function longHrType(overrides: Partial<HrRunType> = {}): HrRunType {
  return hrType({
    typeKey: 'long',
    label: 'Long runs',
    kind: 'long',
    runCount: 5,
    points: hrType().points.map((point, i) => ({ ...point, distanceKm: [21.1, 24.0, 32.0][i] })),
    ...overrides,
  });
}

export function paceType(overrides: Partial<PaceTypeComparison> = {}): PaceTypeComparison {
  return {
    typeKey: '5x6min',
    label: '5x6min',
    current: { averageRepPaceSecondsPerKm: 253, runCount: 2 },
    previous: { averageRepPaceSecondsPerKm: 260, runCount: 3 },
    changeSecondsPerKm: -7,
    runsWithoutPace: 0,
    hasEnoughData: true,
    ...overrides,
  };
}

export function dashboard(
  overrides: Partial<RunningDashboardResponse> = {},
): RunningDashboardResponse {
  return {
    sync: {
      status: 'ok',
      lastSyncedAt: '2026-10-29T08:12:03Z',
      retryAfter: null,
      runsPendingDetail: 0,
    },
    weeklyDistance: {
      weeks: [
        week(),
        week({
          weekStart: '2026-10-26',
          weekEnd: '2026-11-01',
          label: '26 Oct',
          isCurrentWeek: true,
        }),
      ],
    },
    heartRateTrend: {
      windowStart: '2026-05-29',
      windowEnd: '2026-10-29',
      minRunsForComparison: 3,
      types: [hrType()],
    },
    workoutPaceComparison: {
      currentWindow: { start: '2026-09-30', end: '2026-10-29' },
      previousWindow: { start: '2026-08-31', end: '2026-09-29' },
      types: [paceType()],
    },
    ...overrides,
  };
}

export function emptyDashboard(): RunningDashboardResponse {
  const base = dashboard();
  return {
    ...base,
    weeklyDistance: {
      weeks: base.weeklyDistance.weeks.map((w) => ({ ...w, runCount: 0, distanceKm: 0 })),
    },
    heartRateTrend: { ...base.heartRateTrend, types: [] },
    workoutPaceComparison: { ...base.workoutPaceComparison, types: [] },
  };
}
