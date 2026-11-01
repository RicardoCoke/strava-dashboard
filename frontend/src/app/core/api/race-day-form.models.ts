// Mirrors the JSON of GET /api/race-day-form
// (backend api/RaceDayFormDtos.kt, docs/features/011-race-day-form/specs.md).

export type DayKind = 'actual' | 'planned';

export interface FormDay {
  /** ISO local date. */
  date: string;
  kind: DayKind;
  load: number;
  fitness: number;
  fatigue: number;
  form: number;
  readyLow: number;
  readyHigh: number;
}

export interface RaceDaySummary {
  date: string;
  kind: DayKind;
  form: number;
  fitness: number;
  peakFitness: number;
  peakDate: string;
  fitnessFromPeak: number;
  /** e.g. "Form +18 · Fitness 61 (-4 from peak)". */
  label: string;
}

export interface ReadyBand {
  lowPercent: number;
  highPercent: number;
}

export interface RaceDayFormResponse {
  today: string;
  raceDate: string;
  chartStart: string;
  chartEnd: string;
  lastActualDate: string;
  projection: 'projected' | 'notEnoughRuns';
  subtEstimated: boolean;
  readyBand: ReadyBand;
  raceDay: RaceDaySummary | null;
  days: FormDay[];
}

function isObject(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null;
}

/** A light shape check, so an unexpected body becomes the error state rather than a broken chart. */
export function isRaceDayFormResponse(value: unknown): value is RaceDayFormResponse {
  if (!isObject(value)) {
    return false;
  }
  const raceDay = value['raceDay'];
  return (
    (value['projection'] === 'projected' || value['projection'] === 'notEnoughRuns') &&
    typeof value['lastActualDate'] === 'string' &&
    typeof value['raceDate'] === 'string' &&
    isObject(value['readyBand']) &&
    (raceDay === null || (isObject(raceDay) && typeof raceDay['label'] === 'string')) &&
    Array.isArray(value['days']) &&
    value['days'].length > 0
  );
}
