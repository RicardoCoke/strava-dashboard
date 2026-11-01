import { palette } from '../../shared/chart/palette';
import { normalizedVo2maxWeeks } from '../../testing/vo2max-fixtures';
import {
  NORMALIZED_VO2MAX_CHART_LABEL,
  buildNormalizedVo2maxOptions,
  normalizedWeekTooltipLines,
} from './normalized-vo2max.options';

interface LineSeries {
  type: string;
  name: string;
  z: number;
  showSymbol: boolean;
  connectNulls: boolean;
  lineStyle: { color: string; width: number; opacity?: number };
  data: (number | null)[];
}

describe('buildNormalizedVo2maxOptions', () => {
  const weeks = normalizedVo2maxWeeks();
  const options = buildNormalizedVo2maxOptions(weeks);
  const series = options.series as LineSeries[];

  it('R11 - axes like Vo2 chart', () => {
    const xAxis = options.xAxis as { type: string; data: string[] };
    expect(xAxis.type).toBe('category');
    expect(xAxis.data[0]).toBe('27 Apr');
    expect(xAxis.data.length).toBe(27);
    const yAxis = options.yAxis as { type: string; name: string; scale: boolean };
    expect(yAxis.type).toBe('value');
    expect(yAxis.name).toBe('VO2max (ml/kg/min)');
    expect(yAxis.scale).toBe(true);
  });

  it('R11 - null weeks break the normalized line', () => {
    expect(series[0].name).toBe('Normalized Vo2');
    expect(series[0].lineStyle.color).toBe(palette.accent);
    expect(series[0].showSymbol).toBe(true);
    expect(series[0].connectNulls).toBe(false);
    expect(series[0].data).toEqual(weeks.map((week) => week.vo2max));
    expect(series[0].data[1]).toBeNull();
    expect(series[0].data).not.toContain(0);
  });

  it('R11 - faint Vo2 comparison series', () => {
    expect(series.length).toBe(2);
    expect(series[1].name).toBe('Vo2');
    expect(series[1].lineStyle).toEqual({ width: 1, color: palette.textMuted, opacity: 0.5 });
    expect(series[1].showSymbol).toBe(false);
    expect(series[1].connectNulls).toBe(false);
    expect(series[1].data).toEqual(weeks.map((week) => week.originalVo2max));
    expect(series[1].z).toBeLessThan(series[0].z);
  });

  it('R11 - legend', () => {
    const legend = options.legend as { data: string[]; textStyle: { color: string } };
    expect(legend.data).toEqual(['Normalized Vo2', 'Vo2']);
    expect(legend.textStyle.color).toBe(palette.textMuted);
  });

  it('R11 - tooltip lines incl. empty weeks', () => {
    expect(normalizedWeekTooltipLines(weeks[0])).toEqual([
      'Week of 27 Apr',
      'Normalized VO2max 51.5 · 3 runs',
      'Vo2 50.0',
    ]);
    expect(normalizedWeekTooltipLines(weeks[1])).toEqual([
      'Week of 4 May',
      'No qualifying runs',
      'Vo2: no qualifying runs',
    ]);
    expect(normalizedWeekTooltipLines({ ...weeks[26], runCount: 1 })[0]).toBe(
      'Week of 26 Oct (so far)',
    );
    const formatter = (options.tooltip as { formatter: (p: unknown) => string }).formatter;
    expect(formatter([{ dataIndex: 0 }])).toBe(
      'Week of 27 Apr<br/>Normalized VO2max 51.5 · 3 runs<br/>Vo2 50.0',
    );
  });

  it('R11 - aria label', () => {
    expect(NORMALIZED_VO2MAX_CHART_LABEL).toBe('Normalized VO2max per week, last 6 months');
  });
});
