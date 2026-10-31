import type { BarSeriesOption, LineSeriesOption } from 'echarts';
import { hrType, paceType, week } from '../../../testing/dashboard-fixtures';
import { palette } from '../../../shared/chart/palette';
import {
  buildHeartRateTrendOptions,
  hrPointTooltip,
  hrTrendText,
} from './heart-rate-trend.options';
import { buildWeeklyDistanceOptions, weekTooltip } from './weekly-distance.options';
import { CURRENT_SERIES, PREVIOUS_SERIES, buildWorkoutPaceOptions } from './workout-pace.options';

type Formatter = (params: unknown) => string;

describe('chart option builders', () => {
  it('R8 - weekly options have km axis, labels, tooltip and current week colour', () => {
    const weeks = [
      week(),
      week({
        weekStart: '2026-10-26',
        weekEnd: '2026-11-01',
        label: '26 Oct',
        isCurrentWeek: true,
      }),
    ];
    const options = buildWeeklyDistanceOptions(weeks);

    expect(options.xAxis).toMatchObject({ type: 'category', data: ['31 Aug', '26 Oct'] });
    expect(options.yAxis).toMatchObject({ name: 'km' });
    const series = (options.series as BarSeriesOption[])[0];
    const colors = (series.data as { itemStyle: { color: string } }[]).map(
      (d) => d.itemStyle.color,
    );
    expect(colors).toEqual([palette.secondary, palette.accent]);

    expect(weekTooltip(weeks[0])).toBe('31 Aug – 6 Sep: 32.4 km · 4 runs');
    expect(weekTooltip(weeks[1])).toBe('26 Oct – 1 Nov: 32.4 km · 4 runs (this week)');
    const formatter = (options.tooltip as { formatter: Formatter }).formatter;
    expect(formatter({ dataIndex: 0 })).toBe('31 Aug – 6 Sep: 32.4 km · 4 runs');
  });

  it('R9 - selecting a type shows its points and trend', () => {
    const options = buildHeartRateTrendOptions(hrType());

    expect(options.xAxis).toMatchObject({ type: 'time' });
    expect(options.yAxis).toMatchObject({ name: 'Avg HR (bpm)', scale: true });
    const [points, trend] = options.series as LineSeriesOption[];
    expect((points.data as { value: [string, number] }[]).map((d) => d.value)).toEqual([
      ['2026-06-01', 150],
      ['2026-07-01', 146],
      ['2026-08-01', 142],
    ]);
    expect(trend.lineStyle).toMatchObject({ type: 'dashed' });
    expect(trend.data).toEqual([
      ['2026-06-01', 150],
      ['2026-08-01', 142],
    ]);

    expect(
      hrPointTooltip({
        date: '2026-10-12',
        averageHeartRateBpm: 142.3,
        distanceKm: 8.2,
        treadmill: false,
        rising: false,
      }),
    ).toBe('12 Oct 2026 · 142.3 bpm · 8.2 km');
    expect(
      hrPointTooltip({
        date: '2026-10-12',
        averageHeartRateBpm: 142.3,
        distanceKm: 8.2,
        treadmill: true,
        rising: false,
      }),
    ).toBe('12 Oct 2026 · 142.3 bpm · 8.2 km · Treadmill');
    expect(
      hrTrendText({ startDate: '', startBpm: 150, endDate: '', endBpm: 142, changeBpm: -8 }),
    ).toBe('Trend: −8.0 bpm over the period (lower = fitter)');
  });

  it('R12 - HR options without a trend have no trend series', () => {
    const options = buildHeartRateTrendOptions(hrType({ hasEnoughData: false, trend: null }));
    expect((options.series as LineSeriesOption[]).map((s) => s.name)).not.toContain('Trend');
  });

  it('R11 - pace comparison grouped bars with m ss labels and change text', () => {
    const options = buildWorkoutPaceOptions([
      paceType(),
      paceType({
        typeKey: '4x8min',
        label: '4x8min',
        previous: null,
        hasEnoughData: false,
        changeSecondsPerKm: null,
      }),
    ]);

    expect(options.xAxis).toMatchObject({ type: 'category', data: ['5x6min'] });
    expect(options.yAxis).toMatchObject({ name: 'Pace (min/km), lower is faster' });
    const axisFormatter = (options.yAxis as { axisLabel: { formatter: (v: number) => string } })
      .axisLabel.formatter;
    expect(axisFormatter(253)).toBe('4:13');
    const [previous, current] = options.series as BarSeriesOption[];
    expect(previous.name).toBe(PREVIOUS_SERIES);
    expect(current.name).toBe(CURRENT_SERIES);
    expect(previous.data).toEqual([260]);
    expect(current.data).toEqual([253]);
    const labelFormatter = (current.label as { formatter: Formatter }).formatter;
    expect(labelFormatter({ value: 253 })).toBe('4:13');
  });
});
