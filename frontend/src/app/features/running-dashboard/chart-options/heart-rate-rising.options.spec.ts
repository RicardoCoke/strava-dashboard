import type { LineSeriesOption } from 'echarts';
import { HrPoint } from '../../../core/api/running-dashboard.models';
import { palette } from '../../../shared/chart/palette';
import { hrType } from '../../../testing/dashboard-fixtures';
import { buildHeartRateTrendOptions, hrLineStretches } from './heart-rate-trend.options';

// Invented bpm values: 003 specs.md Example A.
const exampleBpm = [150, 150, 150, 151, 152, 153, 154, 148, 146, 146];
const exampleRising = [false, false, false, true, true, true, true, false, false, false];

function points(rising: boolean[], treadmill: boolean[] = []): HrPoint[] {
  return rising.map((isRising, i) => ({
    date: `2026-10-${String(i + 1).padStart(2, '0')}`,
    averageHeartRateBpm: exampleBpm[i] ?? 150,
    distanceKm: 8,
    treadmill: treadmill[i] ?? false,
    rising: isRising,
  }));
}

function stretchSeries(series: LineSeriesOption[]): LineSeriesOption[] {
  return series.filter((s) => s.name === 'HR line');
}

describe('HR rising stretches', () => {
  it('R4 - example A gives accent red accent stretches sharing joints', () => {
    const example = points(exampleRising);
    const stretches = hrLineStretches(example);
    const at = (i: number): [string, number] => [example[i].date, example[i].averageHeartRateBpm];

    expect(stretches).toEqual([
      { color: palette.accent, data: [at(0), at(1), at(2)] },
      { color: palette.worse, data: [at(2), at(3), at(4), at(5), at(6)] },
      { color: palette.accent, data: [at(6), at(7), at(8), at(9)] },
    ]);

    const series = buildHeartRateTrendOptions(hrType({ points: example }))
      .series as LineSeriesOption[];
    expect(stretchSeries(series).map((s) => s.lineStyle)).toEqual([
      { width: 1.5, color: palette.accent },
      { width: 1.5, color: palette.worse },
      { width: 1.5, color: palette.accent },
    ]);
  });

  it('R4 - no rising points gives one accent stretch', () => {
    const flat = points([false, false, false, false]);
    expect(hrLineStretches(flat)).toEqual([
      { color: palette.accent, data: flat.map((p) => [p.date, p.averageHeartRateBpm]) },
    ]);
  });

  it('R4 - single point has no line stretches', () => {
    const series = buildHeartRateTrendOptions(
      hrType({ points: points([false]), trend: null, hasEnoughData: false }),
    ).series as LineSeriesOption[];
    expect(stretchSeries(series)).toEqual([]);
    expect(series[0].data).toHaveLength(1);
  });

  it('R4 - stretch series are silent and tooltip and trend unchanged', () => {
    const options = buildHeartRateTrendOptions(hrType({ points: points(exampleRising) }));
    const series = options.series as LineSeriesOption[];

    stretchSeries(series).forEach((s) => {
      expect(s).toMatchObject({ silent: true, symbol: 'none', z: 2 });
    });
    expect(series[0]).toMatchObject({ z: 3, lineStyle: { width: 0 } });
    expect(series[1]).toMatchObject({
      name: 'Trend',
      lineStyle: { type: 'dashed', width: 1, color: palette.textMuted },
    });
    const formatter = (options.tooltip as { formatter: (p: unknown) => string }).formatter;
    expect(formatter({ seriesIndex: 0, dataIndex: 3 })).toBe('4 Oct 2026 · 151.0 bpm · 8.0 km');
  });

  it('R5 - rising dots are red-orange and treadmill dots stay purple', () => {
    const mixed = points([false, true, true, false], [false, false, true, true]);
    const series = buildHeartRateTrendOptions(hrType({ points: mixed }))
      .series as LineSeriesOption[];
    const styles = (series[0].data as { itemStyle: unknown }[]).map((d) => d.itemStyle);
    const glow = (color: string) => ({ color, shadowBlur: 10, shadowColor: color });

    expect(styles).toEqual([
      glow(palette.accent),
      glow(palette.worse),
      glow(palette.treadmill),
      glow(palette.treadmill),
    ]);
  });
});
