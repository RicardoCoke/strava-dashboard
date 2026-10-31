import type { BarSeriesOption, EChartsOption, LineSeriesOption } from 'echarts';
import { palette } from '../../../shared/chart/palette';
import { hrType, paceType, week } from '../../../testing/dashboard-fixtures';
import { buildHeartRateTrendOptions } from './heart-rate-trend.options';
import { buildWeeklyDistanceOptions } from './weekly-distance.options';
import { buildWorkoutPaceOptions } from './workout-pace.options';

const weeks = [
  week(),
  week({ weekStart: '2026-10-26', weekEnd: '2026-11-01', label: '26 Oct', isCurrentWeek: true }),
];

function expectDarkStyle(options: EChartsOption): void {
  expect(options.backgroundColor).toBe('transparent');
  expect(options.textStyle).toMatchObject({ color: palette.textMuted });
  const x = options.xAxis as Record<string, unknown>;
  const y = options.yAxis as Record<string, unknown>;
  [x, y].forEach((axis) => {
    expect(axis['axisLine']).toEqual({ show: false });
    expect(axis['axisTick']).toEqual({ show: false });
    expect(axis['axisLabel']).toMatchObject({ color: palette.textMuted });
  });
  expect(y['splitLine']).toEqual({ lineStyle: { color: palette.grid, width: 1, type: 'dashed' } });
  expect(x['splitLine']).toEqual({ show: false });
  expect(options.tooltip).toMatchObject({
    backgroundColor: palette.surfaceRaised,
    borderColor: palette.border,
    textStyle: { color: palette.text },
  });
}

describe('dark chart styling', () => {
  it('002 R9 - all charts use dark axis grid and tooltip style', () => {
    expectDarkStyle(buildWeeklyDistanceOptions(weeks));
    expectDarkStyle(buildHeartRateTrendOptions(hrType()));
    expectDarkStyle(buildWorkoutPaceOptions([paceType()]));
  });

  it('002 R9 - weekly bars secondary with accent current week', () => {
    const series = (buildWeeklyDistanceOptions(weeks).series as BarSeriesOption[])[0];
    expect(series.barMaxWidth).toBe(28);
    const styles = (series.data as { itemStyle: { color: string; borderRadius: number[] } }[]).map(
      (d) => d.itemStyle,
    );
    expect(styles).toEqual([
      { color: palette.secondary, borderRadius: [4, 4, 0, 0] },
      { color: palette.accent, borderRadius: [4, 4, 0, 0] },
    ]);
  });

  it('002 R9 - hr points are thin glowing line with treadmill in purple', () => {
    const [points, trend, line] = buildHeartRateTrendOptions(hrType()).series as LineSeriesOption[];
    // 003: the points series draws dots only; the line is a separate stretch series.
    expect(points.lineStyle).toEqual({ width: 0 });
    expect(line.lineStyle).toEqual({ width: 1.5, color: palette.accent });
    expect([points.symbol, points.symbolSize]).toEqual(['circle', 6]);
    const styles = (points.data as { itemStyle: unknown }[]).map((d) => d.itemStyle);
    expect(styles).toEqual([
      { color: palette.accent, shadowBlur: 10, shadowColor: palette.accent },
      { color: palette.treadmill, shadowBlur: 10, shadowColor: palette.treadmill },
      { color: palette.accent, shadowBlur: 10, shadowColor: palette.accent },
    ]);
    expect(trend.lineStyle).toEqual({ type: 'dashed', width: 1, color: palette.textMuted });
  });

  it('002 R9 - pace bars secondary and accent with rounded tops', () => {
    const options = buildWorkoutPaceOptions([paceType()]);
    const [previous, current] = options.series as BarSeriesOption[];
    expect(previous.itemStyle).toEqual({ color: palette.secondary, borderRadius: [4, 4, 0, 0] });
    expect(current.itemStyle).toEqual({ color: palette.accent, borderRadius: [4, 4, 0, 0] });
    expect([previous.barMaxWidth, current.barMaxWidth]).toEqual([28, 28]);
    expect(current.label).toMatchObject({ color: palette.text });
    expect(options.legend).toMatchObject({ textStyle: { color: palette.textMuted } });
  });
});
