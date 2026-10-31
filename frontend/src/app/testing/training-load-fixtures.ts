import { LoadDay, TrainingLoadResponse, WeekRamp } from '../core/api/training-load.models';

// Invented training load data for frontend tests (today = Sat 2026-10-31).

function addDays(isoDate: string, days: number): string {
  const [year, month, day] = isoDate.split('-').map(Number);
  return new Date(Date.UTC(year, month - 1, day + days)).toISOString().slice(0, 10);
}

export function weekRamp(overrides: Partial<WeekRamp> = {}): WeekRamp {
  return {
    weekStart: '2026-10-26',
    weekEnd: '2026-11-01',
    weekToDate: true,
    load: 512,
    previousLoad: 449,
    changePercent: 14,
    previousWeekLight: false,
    warning: false,
    changeLabel: '+14% vs last week',
    ...overrides,
  };
}

/** 185 days from 2026-04-30 to 2026-10-31; the last day matches `latest`. */
export function trainingLoad(overrides: Partial<TrainingLoadResponse> = {}): TrainingLoadResponse {
  const days: LoadDay[] = Array.from({ length: 185 }, (_, i) => ({
    date: addDays('2026-04-30', i),
    load: i % 2 === 0 ? 60 : 0,
    fitness: 41.2,
    fatigue: 38,
    form: 3.2,
  }));
  days[days.length - 1] = { date: '2026-10-31', load: 0, fitness: 52.1, fatigue: 61.3, form: -9.2 };
  return {
    windowStart: '2026-04-30',
    windowEnd: '2026-10-31',
    fitnessDays: 42,
    fatigueDays: 7,
    zones: {
      lthrBpm: 175,
      easyBelowBpm: 153,
      hardAboveBpm: 175,
      label: 'Zones from LTHR 175 bpm: easy < 153 · subT 153–175 · hard > 175',
    },
    latest: { date: '2026-10-31', fitness: 52.1, fatigue: 61.3, form: -9.2 },
    days,
    ramp: {
      thisWeek: weekRamp(),
      lastWeek: weekRamp({
        weekStart: '2026-10-19',
        weekEnd: '2026-10-25',
        weekToDate: false,
        load: 449,
        previousLoad: 401,
        changePercent: 12,
        changeLabel: '+12% vs the week before',
      }),
    },
    runsCounted: 96,
    runsSkippedNoHeartRate: 2,
    runsPendingHeartRate: 0,
    ...overrides,
  };
}
