import {
  HeartRateTrend,
  RunningDashboardResponse,
  WeekTotal,
  WorkoutPaceComparison,
} from '../../../core/api/running-dashboard.models';
import { formatBpmChange } from '../../../shared/format/heart-rate';
import { formatPace } from '../../../shared/format/pace';
import { Tone, toneForChange } from '../../../shared/format/tone';
import { comparablePaceTypes } from '../chart-options/workout-pace.options';

export type HeadlineTileId = 'thisWeek' | 'easyHrTrend' | 'workoutPace';

export interface HeadlineTile {
  id: HeadlineTileId;
  label: string;
  /** "—" when not available. */
  value: string;
  unit: string | null;
  /** Detail line, or the "not available" note. */
  detail: string;
  tone: Tone;
  available: boolean;
}

const NOT_AVAILABLE = '—';
const EASY_TYPE_KEY = 'easy';

function unavailable(id: HeadlineTileId, label: string, detail: string): HeadlineTile {
  return { id, label, value: NOT_AVAILABLE, unit: null, detail, tone: 'neutral', available: false };
}

function plural(count: number, word: string): string {
  return `${count} ${word}${count === 1 ? '' : 's'}`;
}

/** The week the weekly chart highlights; its km is shown as sent. */
function thisWeekTile(weeks: WeekTotal[]): HeadlineTile {
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
    detail: plural(current.runCount, 'run'),
    tone: 'neutral',
    available: true,
  };
}

/** The easy-run trend from the HR chart, whatever type is selected there. */
function easyHrTile(trend: HeartRateTrend): HeadlineTile {
  const label = 'Easy-run HR trend';
  const easy = trend.types.find((type) => type.typeKey === EASY_TYPE_KEY);
  if (!easy) {
    return unavailable('easyHrTrend', label, 'No easy runs in the last 5 months');
  }
  if (!easy.hasEnoughData || !easy.trend) {
    const withHr = plural(easy.points.length, 'easy run');
    return unavailable(
      'easyHrTrend',
      label,
      `Not enough data yet: ${withHr} with heart rate, at least ${trend.minRunsForComparison} needed.`,
    );
  }
  const change = easy.trend.changeBpm;
  return {
    id: 'easyHrTrend',
    label,
    value: formatBpmChange(change),
    unit: 'bpm',
    detail: 'Easy runs · last 5 months',
    tone: toneForChange(change),
    available: true,
  };
}

/** The first type the pace chart draws (backend order: most recent run first). */
function workoutPaceTile(comparison: WorkoutPaceComparison): HeadlineTile {
  const label = 'Workout pace';
  if (comparison.types.length === 0) {
    return unavailable('workoutPace', label, 'No workouts (e.g. 5x6min) in the last 60 days');
  }
  const [chosen] = comparablePaceTypes(comparison.types);
  if (!chosen) {
    return unavailable(
      'workoutPace',
      label,
      'No workout type with paced runs in both 30-day periods yet',
    );
  }
  // Comparable types always have a backend-computed change.
  const change = chosen.type.changeSecondsPerKm ?? 0;
  const detail = `${chosen.type.label} · ${formatPace(chosen.previous)} → ${formatPace(chosen.current)} /km`;
  const tone = toneForChange(change);
  const [value, unit] =
    tone === 'neutral'
      ? ['Same', 'pace']
      : [String(Math.abs(change)), `s/km ${tone === 'better' ? 'faster' : 'slower'}`];
  return { id: 'workoutPace', label, value, unit, detail, tone, available: true };
}

/** Always three tiles, in display order. Values are picked from the response, not recomputed. */
export function buildHeadlineTiles(data: RunningDashboardResponse): HeadlineTile[] {
  return [
    thisWeekTile(data.weeklyDistance.weeks),
    easyHrTile(data.heartRateTrend),
    workoutPaceTile(data.workoutPaceComparison),
  ];
}
