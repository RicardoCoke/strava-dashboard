import { RunningDashboardResponse } from '../../../core/api/running-dashboard.models';
import { formatPaceChange } from '../../../shared/format/pace';
import { dashboard, hrType, paceType, week } from '../../../testing/dashboard-fixtures';
import { hrTrendText } from '../chart-options/heart-rate-trend.options';
import { HeadlineTile, buildHeadlineTiles } from './headline-tiles.model';

function tile(data: RunningDashboardResponse, id: HeadlineTile['id']): HeadlineTile {
  const found = buildHeadlineTiles(data).find((t) => t.id === id);
  if (!found) {
    throw new Error(`no tile ${id}`);
  }
  return found;
}

const currentWeek = (overrides = {}) =>
  week({
    weekStart: '2026-10-26',
    weekEnd: '2026-11-01',
    label: '26 Oct',
    isCurrentWeek: true,
    ...overrides,
  });

function withWeeks(weeks: ReturnType<typeof week>[]): RunningDashboardResponse {
  return dashboard({ weeklyDistance: { weeks } });
}

function withHrTypes(types: ReturnType<typeof hrType>[]): RunningDashboardResponse {
  const base = dashboard();
  return { ...base, heartRateTrend: { ...base.heartRateTrend, types } };
}

function withPaceTypes(types: ReturnType<typeof paceType>[]): RunningDashboardResponse {
  const base = dashboard();
  return { ...base, workoutPaceComparison: { ...base.workoutPaceComparison, types } };
}

describe('buildHeadlineTiles', () => {
  it('002 R4 - always three tiles in order', () => {
    expect(buildHeadlineTiles(dashboard()).map((t) => t.label)).toEqual([
      'This week',
      'Easy-run HR trend',
      'Workout pace',
    ]);
  });

  it('002 R5 - this week tile shows current week km and runs', () => {
    const t = tile(withWeeks([week(), currentWeek({ distanceKm: 32.4, runCount: 4 })]), 'thisWeek');
    expect([t.value, t.unit, t.detail, t.tone]).toEqual(['32.4', 'km', '4 runs', 'neutral']);
  });

  it('002 R5 - zero and one run wording', () => {
    const one = tile(withWeeks([currentWeek({ distanceKm: 5, runCount: 1 })]), 'thisWeek');
    expect([one.value, one.detail]).toEqual(['5.0', '1 run']);
    const zero = tile(withWeeks([currentWeek({ distanceKm: 0, runCount: 0 })]), 'thisWeek');
    expect([zero.value, zero.unit, zero.detail, zero.available]).toEqual([
      '0.0',
      'km',
      '0 runs',
      true,
    ]);
  });

  it('002 R5 - this week tile matches highlighted bar', () => {
    const weeks = [week({ distanceKm: 40.1 }), currentWeek({ distanceKm: 12.345 })];
    const highlighted = weeks.find((w) => w.isCurrentWeek);
    expect(tile(withWeeks(weeks), 'thisWeek').value).toBe(highlighted?.distanceKm.toFixed(1));
  });

  it('002 R5 - missing current week shows dash', () => {
    const t = tile(withWeeks([week()]), 'thisWeek');
    expect([t.value, t.unit, t.detail, t.tone]).toEqual([
      '—',
      null,
      'No data for this week',
      'neutral',
    ]);
  });

  it('002 R6 - easy hr tile shows signed change and tone', () => {
    const better = tile(withHrTypes([hrType()]), 'easyHrTrend');
    expect([better.value, better.unit, better.detail, better.tone]).toEqual([
      '−8.0',
      'bpm',
      'Easy runs · last 5 months',
      'better',
    ]);
    const trendOf = (changeBpm: number) =>
      tile(withHrTypes([hrType({ trend: { ...hrType().trend!, changeBpm } })]), 'easyHrTrend');
    expect([trendOf(3.2).value, trendOf(3.2).tone]).toEqual(['+3.2', 'worse']);
    expect([trendOf(0).value, trendOf(0).tone]).toEqual(['0.0', 'neutral']);
    expect([trendOf(-0).value, trendOf(-0).tone]).toEqual(['0.0', 'neutral']);
  });

  it('002 R6 - easy hr tile not enough data note', () => {
    const points = hrType().points;
    const two = tile(
      withHrTypes([hrType({ hasEnoughData: false, trend: null, points: points.slice(0, 2) })]),
      'easyHrTrend',
    );
    expect([two.value, two.unit, two.detail, two.tone]).toEqual([
      '—',
      null,
      'Not enough data yet: 2 easy runs with heart rate, at least 3 needed.',
      'neutral',
    ]);
    const one = tile(
      withHrTypes([hrType({ hasEnoughData: false, trend: null, points: points.slice(0, 1) })]),
      'easyHrTrend',
    );
    expect(one.detail).toBe('Not enough data yet: 1 easy run with heart rate, at least 3 needed.');
  });

  it('002 R6 - no easy runs shows dash', () => {
    const workoutOnly = withHrTypes([
      hrType({ typeKey: '5x6min', label: '5x6min', kind: 'workout' }),
    ]);
    expect(tile(workoutOnly, 'easyHrTrend').detail).toBe('No easy runs in the last 5 months');
    expect(tile(withHrTypes([]), 'easyHrTrend').value).toBe('—');
  });

  it('002 R6 - tile and trend note use same bpm format', () => {
    const type = hrType();
    const value = tile(withHrTypes([type]), 'easyHrTrend').value;
    expect(hrTrendText(type.trend!)).toContain(`${value} bpm`);
  });

  it('002 R7 - workout tile uses most recent comparable type', () => {
    const t = tile(
      withPaceTypes([
        paceType({
          typeKey: '4x8min',
          label: '4x8min',
          previous: null,
          hasEnoughData: false,
          changeSecondsPerKm: null,
        }),
        paceType(),
        paceType({ typeKey: '2x10min', label: '2x10min', changeSecondsPerKm: 3 }),
      ]),
      'workoutPace',
    );
    expect([t.value, t.unit, t.detail, t.tone]).toEqual([
      '7',
      's/km faster',
      '5x6min · 4:20 → 4:13 /km',
      'better',
    ]);
  });

  it('002 R7 - slower and same pace wording', () => {
    const slower = tile(withPaceTypes([paceType({ changeSecondsPerKm: 3 })]), 'workoutPace');
    expect([slower.value, slower.unit, slower.tone]).toEqual(['3', 's/km slower', 'worse']);
    const same = tile(withPaceTypes([paceType({ changeSecondsPerKm: 0 })]), 'workoutPace');
    expect([same.value, same.unit, same.tone]).toEqual(['Same', 'pace', 'neutral']);
  });

  it('002 R7 - no comparable workout shows dash', () => {
    const t = tile(
      withPaceTypes([paceType({ previous: null, hasEnoughData: false, changeSecondsPerKm: null })]),
      'workoutPace',
    );
    expect([t.value, t.detail]).toEqual([
      '—',
      'No workout type with paced runs in both 30-day periods yet',
    ]);
  });

  it('002 R7 - no workouts shows dash', () => {
    const t = tile(withPaceTypes([]), 'workoutPace');
    expect([t.value, t.detail]).toEqual(['—', 'No workouts (e.g. 5x6min) in the last 60 days']);
  });

  it('002 R7 - workout tile text matches pace card change text', () => {
    [-7, 3].forEach((change) => {
      const t = tile(withPaceTypes([paceType({ changeSecondsPerKm: change })]), 'workoutPace');
      expect(`${t.value} ${t.unit}`).toBe(formatPaceChange(change));
    });
  });
});
