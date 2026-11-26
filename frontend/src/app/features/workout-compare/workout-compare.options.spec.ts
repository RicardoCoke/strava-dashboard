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

    // Each run at its own nearest point: earlier 0.027 km, later 0.029 km.
    expect(compareTooltipLines(0.03, data, 'distance')).toEqual([
      '0.03 km',
      'Earlier · 10 Nov: 6:10 /km · 101 bpm',
      'Later · 24 Nov: 5:45 /km · 98 bpm',
    ]);
    expect(compareTooltipLines(20 / 60, data, 'time')).toEqual([
      '0:20',
      'Earlier · 10 Nov: 5:57 /km',
      'Later · 24 Nov: — /km · 103 bpm',
    ]);
    // The earlier run hidden from the legend: only the later run's line.
    expect(compareTooltipLines(10 / 60, data, 'time', { 'Earlier · 10 Nov': false })).toEqual([
      '0:10',
      'Later · 24 Nov: 5:45 /km · 98 bpm',
    ]);
    // Past the earlier run's last point (0.055 km), only the later run has a value.
    expect(compareTooltipLines(0.057, data, 'distance')).toEqual([
      '0.06 km',
      'Later · 24 Nov: — /km · 103 bpm',
    ]);
    expect(compareTooltipLines(0.03, data, 'distance').join(' ')).not.toContain('threshold');
  });

  it('R7 - tooltip shows both runs when ECharts passes only the nearest series', () => {
    // On a value x-axis ECharts keeps only the series whose x is closest to the pointer: here the earlier run's.
    const options = buildCompareOptions(comparison(), 'distance');
    const formatter = (options.tooltip as { formatter: (params: unknown) => string }).formatter;
    const params = [0, 2].map((seriesIndex) => ({ seriesIndex, dataIndex: 1, axisValue: 0.055 }));

    expect(formatter(params).split('<br/>').slice(1)).toEqual([
      'Earlier · 10 Nov: 5:57 /km',
      'Later · 24 Nov: — /km · 103 bpm',
    ]);
  });

  it('R7 - legend selection is kept in rebuilt options', () => {
    const shown = { 'Later · 24 Nov': false };
    expect(
      (buildCompareOptions(comparison(), 'time', shown).legend as { selected: object }).selected,
    ).toEqual(shown);
  });

  it('R7 - tooltip reads the legend selection on each hover', () => {
    const shown: Record<string, boolean> = {};
    const options = buildCompareOptions(comparison(), 'distance', shown);
    const formatter = (options.tooltip as { formatter: (params: unknown) => string }).formatter;
    const lines = () =>
      formatter([{ seriesIndex: 0, dataIndex: 0, axisValue: 0.03 }]).split('<br/>');

    expect(lines().length).toBe(3);
    shown['Earlier · 10 Nov'] = false; // a legend click, without new options
    expect(lines()).toEqual(['0.03 km', 'Later · 24 Nov: 5:45 /km · 98 bpm']);
  });

  it('R7 - 2x and 4x show part of the x axis with a scroll bar', () => {
    const zoomed = (zoom: 1 | 2 | 4) => buildCompareOptions(comparison(), 'distance', {}, zoom);
    const bar = (zoom: 2 | 4) => (zoomed(zoom).dataZoom as Record<string, unknown>[])[0];

    expect(zoomed(1).dataZoom).toBeUndefined();
    expect((zoomed(1).grid as { bottom: number }).bottom).toBe(40);
    expect(bar(2)).toEqual(
      expect.objectContaining({
        type: 'slider',
        xAxisIndex: 0,
        start: 0,
        end: 50,
        zoomLock: true,
        // 'none' keeps both y-axes still while scrolling; the slider default 'filter' rescales them.
        filterMode: 'none',
      }),
    );
    expect(bar(4)).toEqual(expect.objectContaining({ start: 0, end: 25, filterMode: 'none' }));
    expect((zoomed(4).grid as { bottom: number }).bottom).toBe(80);
  });

  it('R7 - single x axis so touch zoom applies', () => {
    expect(Array.isArray(buildCompareOptions(comparison(), 'distance').xAxis)).toBe(false);
  });

  it('R7 - chart label follows the axis', () => {
    expect(compareChartLabel('distance')).toBe('Pace and heart rate of two runs by distance');
    expect(compareChartLabel('time')).toBe('Pace and heart rate of two runs by time');
  });
});
