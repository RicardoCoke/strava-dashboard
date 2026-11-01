import type { LineSeriesOption, ScatterSeriesOption } from 'echarts';
import { palette } from '../../shared/chart/palette';
import { efPoint, efficiencyFactor } from '../../testing/efficiency-factor-fixtures';
import {
  EASY_SERIES,
  LONG_SERIES,
  ROLLING_SERIES,
  buildEfficiencyFactorOptions,
  efPointTooltip,
} from './efficiency-factor.options';

type Series = (LineSeriesOption | ScatterSeriesOption)[];

describe('buildEfficiencyFactorOptions', () => {
  const series = (options: ReturnType<typeof buildEfficiencyFactorOptions>) =>
    options.series as Series;
  const legend = (options: ReturnType<typeof buildEfficiencyFactorOptions>) =>
    (options.legend as { data: string[] }).data;

  it('014 R9 - easy and long scatter series with legend for present types', () => {
    const options = buildEfficiencyFactorOptions(efficiencyFactor().points);
    const [line, easy, long] = series(options);
    expect(line).toEqual(
      expect.objectContaining({ type: 'line', name: ROLLING_SERIES, silent: true }),
    );
    expect(easy).toEqual(expect.objectContaining({ type: 'scatter', name: EASY_SERIES }));
    expect(easy.itemStyle?.color).toBe(palette.accent);
    expect(long).toEqual(expect.objectContaining({ type: 'scatter', name: LONG_SERIES }));
    expect(long.itemStyle?.color).toBe(palette.longRun);
    expect(legend(options)).toEqual([ROLLING_SERIES, EASY_SERIES, LONG_SERIES]);

    const easyOnly = buildEfficiencyFactorOptions([efPoint({ runType: 'easy' })]);
    expect(series(easyOnly).map((s) => s.name)).toEqual([ROLLING_SERIES, EASY_SERIES]);
    expect(legend(easyOnly)).toEqual([ROLLING_SERIES, EASY_SERIES]);
    const longOnly = buildEfficiencyFactorOptions([efPoint({ rollingEfficiency: null })]);
    expect(legend(longOnly)).toEqual([LONG_SERIES]);
  });

  it('014 R9 - null rolling values leave gaps in the line', () => {
    const [line, easy] = series(buildEfficiencyFactorOptions(efficiencyFactor().points));
    expect((line as LineSeriesOption).connectNulls).toBe(false);
    expect(line.data).toEqual([
      ['2026-05-01', null],
      ['2026-05-03', 1.585],
      ['2026-10-25', 1.66],
      ['2026-10-29', 1.652],
    ]);
    expect(easy.data).toEqual([
      ['2026-05-01', 1.57],
      ['2026-05-03', 1.598],
      ['2026-10-25', 1.67],
    ]);
  });

  it('014 R9 - time x axis and EF y axis with units', () => {
    const options = buildEfficiencyFactorOptions(efficiencyFactor().points);
    expect(options.xAxis).toEqual(expect.objectContaining({ type: 'time' }));
    const yAxis = options.yAxis as {
      type: string;
      scale: boolean;
      name: string;
      axisLabel: { formatter: (value: number) => string };
    };
    expect(yAxis.type).toBe('value');
    expect(yAxis.scale).toBe(true);
    expect(yAxis.name).toBe('EF (m/min per bpm), higher is better');
    expect(yAxis.axisLabel.formatter(1.6)).toBe('1.60');
  });

  it('014 R9 - tooltip shows date, type, EF, km, pace, HR and rolling', () => {
    expect(efPointTooltip(efPoint())).toBe(
      '29 Oct 2026 · Long run · EF 1.62 · 22.4 km · 5:31 /km · 142 bpm · 4-week avg 1.65',
    );
    expect(efPointTooltip(efPoint({ runType: 'easy', rollingEfficiency: null }))).toBe(
      '29 Oct 2026 · Easy · EF 1.62 · 22.4 km · 5:31 /km · 142 bpm',
    );
    const options = buildEfficiencyFactorOptions(efficiencyFactor().points);
    const formatter = (options.tooltip as { formatter: (p: object) => string }).formatter;
    expect(formatter({ seriesIndex: 2, dataIndex: 0 })).toBe(efPointTooltip(efPoint()));
    expect(formatter({ seriesIndex: 0, dataIndex: 0 })).toBe('');
  });
});
