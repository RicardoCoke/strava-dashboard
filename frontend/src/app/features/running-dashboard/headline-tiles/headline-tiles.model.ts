import {
  DistanceDifference,
  EasyHrWeekComparison,
  RunningDashboardResponse,
  WeekMissing,
  WeekTotal,
  WorkoutPaceWeekComparison,
} from '../../../core/api/running-dashboard.models';
import { formatKmDifference } from '../../../shared/format/distance';
import { formatBpmChange } from '../../../shared/format/heart-rate';
import { formatPace } from '../../../shared/format/pace';
import { Tone, toneForChange } from '../../../shared/format/tone';

export type HeadlineTileId = 'thisWeek' | 'easyHrTrend' | 'workoutPace';

/** Coloured part of the detail line; fewer km is muted, never "worse". */
export interface TileDelta {
  text: string;
  tone: 'better' | 'muted';
}

export interface HeadlineTile {
  id: HeadlineTileId;
  label: string;
  /** "—" when not available. */
  value: string;
  unit: string | null;
  /** "Last → this" at the start of the detail line ("This week" tile only). */
  comparison: string | null;
  /** Shown after [comparison] ("This week" tile only). */
  delta: TileDelta | null;
  /** Detail line, or the "not available" note. */
  detail: string;
  tone: Tone;
  available: boolean;
}

const NOT_AVAILABLE = '—';

const MISSING_SUFFIX: Record<WeekMissing, string> = {
  thisWeek: 'this week yet',
  lastWeek: 'last week',
  bothWeeks: 'this week or last week',
};

function unavailable(id: HeadlineTileId, label: string, detail: string): HeadlineTile {
  return {
    id,
    label,
    value: NOT_AVAILABLE,
    unit: null,
    comparison: null,
    delta: null,
    detail,
    tone: 'neutral',
    available: false,
  };
}

function plural(count: number, word: string): string {
  return `${count} ${word}${count === 1 ? '' : 's'}`;
}

/** The week the weekly chart highlights; its km is shown as sent, with last week's beneath it like the HR tile. */
function thisWeekTile(weeks: WeekTotal[], distance: DistanceDifference): HeadlineTile {
  const label = 'This week';
  const current = weeks.find((week) => week.isCurrentWeek);
  if (!current) {
    return unavailable('thisWeek', label, 'No data for this week');
  }
  return {
    id: 'thisWeek',
    label,
    value: current.distanceKm.toFixed(1),
    unit: 'km',
    comparison: `${distance.lastWeekKm.toFixed(1)} → ${current.distanceKm.toFixed(1)} km`,
    delta: {
      text: formatKmDifference(distance.differenceKm),
      tone: distance.differenceKm > 0 ? 'better' : 'muted',
    },
    detail: plural(current.runCount, 'run'),
    tone: 'neutral',
    available: true,
  };
}

/** This week's mean easy-run HR against last week's, whatever type the HR chart shows. */
function easyHrTile(comparison: EasyHrWeekComparison): HeadlineTile {
  const label = 'Easy-run HR';
  const { thisWeek, lastWeek, changeBpm } = comparison;
  if (!thisWeek || !lastWeek || changeBpm === null) {
    const missing = comparison.missingIn ?? 'bothWeeks';
    return unavailable(
      'easyHrTrend',
      label,
      `No easy run with heart rate ${MISSING_SUFFIX[missing]}`,
    );
  }
  return {
    id: 'easyHrTrend',
    label,
    value: formatBpmChange(changeBpm),
    unit: 'bpm',
    comparison: null,
    delta: null,
    detail: `${lastWeek.averageHeartRateBpm.toFixed(1)} → ${thisWeek.averageHeartRateBpm.toFixed(1)} bpm`,
    tone: toneForChange(changeBpm),
    available: true,
  };
}

/** Mean rep pace over all of this week's workouts against last week's, across workout types. */
function workoutPaceTile(comparison: WorkoutPaceWeekComparison): HeadlineTile {
  const label = 'Workout pace';
  const { thisWeek, lastWeek, changeSecondsPerKm: change } = comparison;
  if (!thisWeek || !lastWeek || change === null) {
    const missing = comparison.missingIn ?? 'bothWeeks';
    return unavailable(
      'workoutPace',
      label,
      `No workout with a measured pace ${MISSING_SUFFIX[missing]}`,
    );
  }
  const types = `${lastWeek.typeLabels.join(', ')} → ${thisWeek.typeLabels.join(', ')}`;
  const paces = `${formatPace(lastWeek.averageRepPaceSecondsPerKm)} → ${formatPace(thisWeek.averageRepPaceSecondsPerKm)} /km`;
  const tone = toneForChange(change);
  const [value, unit] =
    tone === 'neutral'
      ? ['Same', 'pace']
      : [String(Math.abs(change)), `s/km ${tone === 'better' ? 'faster' : 'slower'}`];
  return {
    id: 'workoutPace',
    label,
    value,
    unit,
    comparison: null,
    delta: null,
    detail: `${types} · ${paces}`,
    tone,
    available: true,
  };
}

/** Always three tiles, in display order. Values are picked from the response, not recomputed. */
export function buildHeadlineTiles(data: RunningDashboardResponse): HeadlineTile[] {
  const week = data.weekComparison;
  return [
    thisWeekTile(data.weeklyDistance.weeks, week.distance),
    easyHrTile(week.easyHeartRate),
    workoutPaceTile(week.workoutPace),
  ];
}
