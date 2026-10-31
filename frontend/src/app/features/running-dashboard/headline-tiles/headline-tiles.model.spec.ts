import {
  EasyHrWeekComparison,
  RunningDashboardResponse,
  WorkoutPaceWeekComparison,
} from '../../../core/api/running-dashboard.models';
import { formatPaceChange } from '../../../shared/format/pace';
import { dashboard, week, weekComparison } from '../../../testing/dashboard-fixtures';
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

function withKmDifference(differenceKm: number): RunningDashboardResponse {
  const base = weekComparison();
  return dashboard({
    weekComparison: { ...base, distance: { ...base.distance, differenceKm } },
  });
}

function withEasyHr(overrides: Partial<EasyHrWeekComparison>): RunningDashboardResponse {
  const base = weekComparison();
  return dashboard({
    weekComparison: { ...base, easyHeartRate: { ...base.easyHeartRate, ...overrides } },
  });
}

function withPace(overrides: Partial<WorkoutPaceWeekComparison>): RunningDashboardResponse {
  const base = weekComparison();
  return dashboard({
    weekComparison: { ...base, workoutPace: { ...base.workoutPace, ...overrides } },
  });
}

describe('buildHeadlineTiles', () => {
  it('002 R4 - always three tiles in order', () => {
    expect(buildHeadlineTiles(dashboard()).map((t) => t.label)).toEqual([
      'This week',
      'Easy-run HR',
      'Workout pace',
    ]);
  });

  it('007 R1 - this week tile keeps km value unit and run count', () => {
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

  it('007 R3 - ahead shows green plus km', () => {
    expect(tile(withKmDifference(3.4), 'thisWeek').delta).toEqual({
      text: '+3.4 km',
      tone: 'better',
    });
  });

  it('007 R3 - behind shows muted minus km', () => {
    expect(tile(withKmDifference(-4.2), 'thisWeek').delta).toEqual({
      text: '−4.2 km',
      tone: 'muted',
    });
  });

  it('007 R3 - this week tile shows last week to this week km like the hr tile', () => {
    const t = tile(dashboard(), 'thisWeek');
    expect([t.comparison, t.delta?.text, t.detail]).toEqual([
      '29.0 → 32.4 km',
      '+3.4 km',
      '4 runs',
    ]);
  });

  it('007 R3 - equal shows plus-minus zero muted', () => {
    [0, -0].forEach((km) => {
      expect(tile(withKmDifference(km), 'thisWeek').delta).toEqual({
        text: '±0.0 km',
        tone: 'muted',
      });
    });
  });

  it('007 R3 - no delta when this week unavailable and none on other tiles', () => {
    const unavailable = tile(withWeeks([week()]), 'thisWeek');
    expect([unavailable.comparison, unavailable.delta]).toEqual([null, null]);
    (['easyHrTrend', 'workoutPace'] as const).forEach((id) => {
      expect([tile(dashboard(), id).comparison, tile(dashboard(), id).delta]).toEqual([null, null]);
    });
  });

  it('007 R5 - hr tile shows signed change detail and tone', () => {
    const better = tile(dashboard(), 'easyHrTrend');
    expect([better.value, better.unit, better.detail, better.tone]).toEqual([
      '−2.1',
      'bpm',
      '141.0 → 138.9 bpm',
      'better',
    ]);
    const worse = tile(
      withEasyHr({
        thisWeek: { averageHeartRateBpm: 140.5, runCount: 2 },
        lastWeek: { averageHeartRateBpm: 139.0, runCount: 3 },
        changeBpm: 1.5,
      }),
      'easyHrTrend',
    );
    expect([worse.value, worse.detail, worse.tone]).toEqual(['+1.5', '139.0 → 140.5 bpm', 'worse']);
    const same = tile(withEasyHr({ changeBpm: 0 }), 'easyHrTrend');
    expect([same.value, same.tone]).toEqual(['0.0', 'neutral']);
  });

  it('007 R7 - pace tile shows change types and paces', () => {
    const t = tile(dashboard(), 'workoutPace');
    expect([t.value, t.unit, t.detail, t.tone]).toEqual([
      '5',
      's/km faster',
      '5x6min → 4x8min · 4:13 → 4:08 /km',
      'better',
    ]);
    const several = tile(
      withPace({
        lastWeek: {
          averageRepPaceSecondsPerKm: 254,
          runCount: 2,
          typeLabels: ['5x6min', '3x10min'],
        },
      }),
      'workoutPace',
    );
    expect(several.detail.startsWith('5x6min, 3x10min → 4x8min · ')).toBe(true);
  });

  it('007 R7 - slower and same wording', () => {
    const slower = tile(withPace({ changeSecondsPerKm: 3 }), 'workoutPace');
    expect([slower.value, slower.unit, slower.tone]).toEqual(['3', 's/km slower', 'worse']);
    const same = tile(withPace({ changeSecondsPerKm: 0 }), 'workoutPace');
    expect([same.value, same.unit, same.tone]).toEqual(['Same', 'pace', 'neutral']);
  });

  it('007 R7 - pace tile text matches the pace card wording', () => {
    [-7, 3].forEach((change) => {
      const t = tile(withPace({ changeSecondsPerKm: change }), 'workoutPace');
      expect(`${t.value} ${t.unit}`).toBe(formatPaceChange(change));
    });
  });

  it('007 R8 - unavailable notes for this week last week and both', () => {
    const hrNote = (missingIn: EasyHrWeekComparison['missingIn']) => {
      const side = { averageHeartRateBpm: 140, runCount: 1 };
      const t = tile(
        withEasyHr({
          thisWeek: missingIn === 'lastWeek' ? side : null,
          lastWeek: missingIn === 'thisWeek' ? side : null,
          changeBpm: null,
          missingIn,
        }),
        'easyHrTrend',
      );
      expect([t.value, t.unit, t.tone, t.available]).toEqual(['—', null, 'neutral', false]);
      return t.detail;
    };
    expect(hrNote('thisWeek')).toBe('No easy run with heart rate this week yet');
    expect(hrNote('lastWeek')).toBe('No easy run with heart rate last week');
    expect(hrNote('bothWeeks')).toBe('No easy run with heart rate this week or last week');

    const paceNote = (missingIn: WorkoutPaceWeekComparison['missingIn']) =>
      tile(
        withPace({ thisWeek: null, lastWeek: null, changeSecondsPerKm: null, missingIn }),
        'workoutPace',
      ).detail;
    expect(paceNote('thisWeek')).toBe('No workout with a measured pace this week yet');
    expect(paceNote('lastWeek')).toBe('No workout with a measured pace last week');
    expect(paceNote('bothWeeks')).toBe('No workout with a measured pace this week or last week');
  });

  it('007 R8 - one unavailable tile leaves the others', () => {
    const tiles = buildHeadlineTiles(
      withPace({ thisWeek: null, changeSecondsPerKm: null, missingIn: 'thisWeek' }),
    );
    expect(tiles.map((t) => t.available)).toEqual([true, true, false]);
  });
});
