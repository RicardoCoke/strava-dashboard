import { palette } from '../../shared/chart/palette';
import { comparison } from '../../testing/workout-compare-fixtures';
import {
  buildCompareOptions,
  compareChartLabel,
  compareTooltipLines,
} from './workout-compare.options';

interface Series {
  name: string;
  yAxisIndex: number;
  showSymbol: boolean;
  connectNulls: boolean;
  lineStyle: { color: string; width: number; type: string };
  data: [number, number | null][];
}

const seriesOf = (options: ReturnType<typeof buildCompareOptions>) =>
  options.series as unknown as Series[];

describe('workout compare chart options', () => {
  it('R7 - four series, two legend entries, run colours and styles', () => {
    const options = buildCompareOptions(comparison(), 'distance');
    const series = seriesOf(options);

    expect(
      series.map((s) => [
        s.name,
        s.yAxisIndex,
        s.lineStyle.color,
        s.lineStyle.type,
        s.lineStyle.width,
      ]),
    ).toEqual([
      ['Earlier · 10 Nov', 0, palette.secondary, 'solid', 2],
      ['Later · 24 Nov', 0, palette.accent, 'solid', 2],
      ['Earlier · 10 Nov', 1, palette.secondary, 'dashed', 1.5],
      ['Later · 24 Nov', 1, palette.accent, 'dashed', 1.5],
    ]);
    expect((options.legend as { data: string[] }).data).toEqual([
      'Earlier · 10 Nov',
      'Later · 24 Nov',
    ]);
    expect(series.every((s) => !s.connectNulls && !s.showSymbol)).toBe(true);
    expect(
      seriesOf(buildCompareOptions(comparison({ resolution: 'splits' }), 'distance'))[0].showSymbol,
    ).toBe(true);
    const [pace, heartRate] = options.yAxis as {
      name: string;
      inverse?: boolean;
      scale: boolean;
    }[];
    expect([pace.name, pace.inverse, pace.scale]).toEqual(['Pace (/km)', true, true]);
    expect([heartRate.name, heartRate.scale]).toEqual(['HR (bpm)', true]);
  });

  it('R7 - x axis switches between distance and time', () => {
    const byDistance = buildCompareOptions(comparison(), 'distance');
    const byTime = buildCompareOptions(comparison(), 'time');

    expect((byDistance.xAxis as { name: string; min: number }).name).toBe('Distance (km)');
    expect((byDistance.xAxis as { min: number }).min).toBe(0);
    expect(seriesOf(byDistance)[0].data).toEqual([
      [0.027, 370],
      [0.055, 357],
    ]);
    expect((byTime.xAxis as { name: string }).name).toBe('Time (min)');
    expect(seriesOf(byTime)[3].data).toEqual([
      [10 / 60, 98],
      [20 / 60, 103],
    ]);
  });

  it('R7 - tooltip lines use labels and numbers only', () => {
    const data = comparison();
    const params = (dataIndex: number) =>
      [0, 1, 2, 3].map((seriesIndex) => ({ seriesIndex, dataIndex }));

    expect(compareTooltipLines(params(0), data, 'distance')).toEqual([
      '0.03 km',
      'Earlier · 10 Nov: 6:10 /km · 101 bpm',
      'Later · 24 Nov: 5:45 /km · 98 bpm',
    ]);
    expect(compareTooltipLines(params(1), data, 'time')).toEqual([
      '0:20',
      'Earlier · 10 Nov: 5:57 /km',
      'Later · 24 Nov: — /km · 103 bpm',
    ]);
    // The earlier run hidden from the legend: only the later run's line.
    expect(compareTooltipLines([{ seriesIndex: 3, dataIndex: 0 }], data, 'time')).toEqual([
      '0:10',
      'Later · 24 Nov: 5:45 /km · 98 bpm',
    ]);
    expect(compareTooltipLines(params(0), data, 'distance').join(' ')).not.toContain('threshold');
  });

  it('R7 - single x axis so touch zoom applies', () => {
    expect(Array.isArray(buildCompareOptions(comparison(), 'distance').xAxis)).toBe(false);
  });

  it('R7 - chart label follows the axis', () => {
    expect(compareChartLabel('distance')).toBe('Pace and heart rate of two runs by distance');
    expect(compareChartLabel('time')).toBe('Pace and heart rate of two runs by time');
  });
});
