import { FormDay, RaceDayFormResponse } from '../core/api/race-day-form.models';

// Invented race-day form data (feature 011). Today Sat 2026-10-31, race Sun 2026-12-06.

function isoDay(start: string, offset: number): string {
  const date = new Date(`${start}T00:00:00Z`);
  date.setUTCDate(date.getUTCDate() + offset);
  return date.toISOString().slice(0, 10);
}

/** [count] days from [start]; days after [lastActual] are planned. */
export function formDays(start: string, count: number, lastActual: string): FormDay[] {
  return Array.from({ length: count }, (_, i) => {
    const date = isoDay(start, i);
    const fitness = 60 + (i % 5);
    const fatigue = 70 - i * 0.4;
    return {
      date,
      kind: date <= lastActual ? 'actual' : 'planned',
      load: i % 7 === 0 ? 0 : 65,
      fitness,
      fatigue,
      form: Math.round((fitness - fatigue) * 10) / 10,
      readyLow: Math.round(fitness * 0.5) / 10,
      readyHigh: Math.round(fitness * 2.5) / 10,
    };
  });
}

export function raceDayForm(overrides: Partial<RaceDayFormResponse> = {}): RaceDayFormResponse {
  return {
    today: '2026-10-31',
    raceDate: '2026-12-06',
    chartStart: '2026-09-19',
    chartEnd: '2026-12-06',
    lastActualDate: '2026-10-30',
    projection: 'projected',
    subtEstimated: false,
    readyBand: { lowPercent: 5, highPercent: 25 },
    raceDay: {
      date: '2026-12-06',
      kind: 'planned',
      form: 17.8,
      fitness: 61.4,
      peakFitness: 65.0,
      peakDate: '2026-10-18',
      fitnessFromPeak: -4,
      label: 'Form +18 · Fitness 61 (-4 from peak)',
    },
    days: formDays('2026-09-19', 79, '2026-10-30'),
    ...overrides,
  };
}

/** Fewer than 3 easy runs: past days only, all actual, no race day. */
export function notEnoughRunsForm(): RaceDayFormResponse {
  return raceDayForm({
    chartEnd: '2026-10-31',
    lastActualDate: '2026-10-31',
    projection: 'notEnoughRuns',
    raceDay: null,
    days: formDays('2026-09-19', 43, '2026-10-31'),
  });
}
