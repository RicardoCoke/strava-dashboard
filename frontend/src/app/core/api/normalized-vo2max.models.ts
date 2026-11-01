// The JSON contract of GET /api/vo2max/normalized (see docs/features/016-normalized-vo2max/specs.md).
// Mirrors backend/src/main/kotlin/app/stravadashboard/api/NormalizedVo2maxDtos.kt.

import {
  CurrentVo2max,
  MaxHr,
  RacePrediction,
  WeekVo2max,
  isVo2maxResponse,
} from './vo2max.models';

/** A week of the normalized series, plus the original "Vo2" value for the comparison line. */
export interface NormalizedWeekVo2max extends WeekVo2max {
  originalVo2max: number | null;
}

export interface NormalizedVo2maxResponse {
  maxHr: MaxHr;
  current: CurrentVo2max | null;
  predictions: RacePrediction[];
  windowStart: string;
  windowEnd: string;
  weeks: NormalizedWeekVo2max[];
  runsPendingDetail: number;
}

/** The 008 shape check, plus every week carrying `originalVo2max` (a number or null). */
export function isNormalizedVo2maxResponse(value: unknown): value is NormalizedVo2maxResponse {
  if (!isVo2maxResponse(value)) {
    return false;
  }
  return (value.weeks as unknown[]).every((week) => {
    if (typeof week !== 'object' || week === null || !('originalVo2max' in week)) {
      return false;
    }
    const original = (week as Record<string, unknown>)['originalVo2max'];
    return original === null || typeof original === 'number';
  });
}
