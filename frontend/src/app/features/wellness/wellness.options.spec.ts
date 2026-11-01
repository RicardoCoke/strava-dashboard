import { palette } from '../../shared/chart/palette';
import { wellness, wellnessDay } from '../../testing/wellness-fixtures';
import {
  WELLNESS_CHART_LABEL,
  buildWellnessOptions,
  wellnessTooltipLines,
} from './wellness.options';

interface LineSeries {
  name: string;
  yAxisIndex: number;
  connectNulls: boolean;
  lineStyle: { type: string; color: string };
  data: (number | null)[];
}

describe('buildWellnessOptions', () => {
  const options = buildWellnessOptions(wellness());
  const series = options.series as LineSeries[];

  it('R9 - four series, dual axes, dashed baselines, nulls kept', () => {
    expect(series.map((s) => s.name)).toEqual([
      'Resting HR',
      'Resting HR 7-day baseline',
      'HRV',
      'HRV 7-day baseline',
    ]);
    expect(series.map((s) => s.yAxisIndex)).toEqual([0, 0, 1, 1]);
    expect(series.map((s) => s.lineStyle.type)).toEqual(['solid', 'dashed', 'solid', 'dashed']);
    series.forEach((s) => {
      expect(s.connectNulls).toBe(false);
      expect(s.data.length).toBe(84);
      expect(s.data.slice(0, 4)).toEqual([null, null, null, null]);
    });
    const yAxis = options.yAxis as { name: string; scale: boolean }[];
    expect(yAxis.map((axis) => axis.name)).toEqual(['Resting HR (bpm)', 'HRV (ms)']);
    expect(yAxis.every((axis) => axis.scale)).toBe(true);
    const xAxis = options.xAxis as { data: string[] };
    expect(xAxis.data.length).toBe(84);
    expect(xAxis.data[0]).toBe('9 Aug');
    expect(xAxis.data[83]).toBe('31 Oct');
  });

  it('R9 - chart label', () => {
    expect(WELLNESS_CHART_LABEL).toBe(
      'Resting heart rate and HRV per day with 7-day baselines, last 12 weeks',
    );
  });

  it('R10 - colours from palette', () => {
    expect(series.map((s) => s.lineStyle.color)).toEqual([
      palette.restingHr,
      palette.restingHr,
      palette.hrv,
      palette.hrv,
    ]);
  });

  it('R9 - tooltip lines for a day', () => {
    expect(
      wellnessTooltipLines(
        wellnessDay({
          restingHrBpm: 52,
          restingHrBaselineBpm: 48.3,
          hrvMs: 61,
          hrvBaselineMs: 58,
        }),
      ),
    ).toEqual(['Sat 31 Oct', 'Resting HR 52 bpm (baseline 48.3)', 'HRV 61 ms (baseline 58.0)']);
    expect(
      wellnessTooltipLines(wellnessDay({ restingHrBpm: null, hrvMs: 61, hrvBaselineMs: null })),
    ).toEqual(['Sat 31 Oct', 'Resting HR no data', 'HRV 61 ms']);
  });
});
