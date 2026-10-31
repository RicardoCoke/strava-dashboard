import { palette } from '../../shared/chart/palette';
import { vo2maxWeeks } from '../../testing/vo2max-fixtures';
import { VO2MAX_CHART_LABEL, buildVo2maxOptions, weekTooltipLines } from './vo2max.options';

interface LineSeries {
  type: string;
  name: string;
  showSymbol: boolean;
  connectNulls: boolean;
  lineStyle: { color: string };
  data: (number | null)[];
}

describe('buildVo2maxOptions', () => {
  const weeks = vo2maxWeeks();
  const options = buildVo2maxOptions(weeks);
  const series = options.series as LineSeries[];

  it('R11 - weekly line in accent colour', () => {
    expect(series.length).toBe(1);
    expect(series[0].type).toBe('line');
    expect(series[0].name).toBe('VO2max');
    expect(series[0].lineStyle.color).toBe(palette.accent);
    expect(series[0].showSymbol).toBe(true);
    const xAxis = options.xAxis as { type: string; data: string[] };
    expect(xAxis.type).toBe('category');
    expect(xAxis.data[0]).toBe('27 Apr');
    expect(xAxis.data.length).toBe(27);
    const yAxis = options.yAxis as { name: string; scale: boolean };
    expect(yAxis.name).toBe('VO2max (ml/kg/min)');
    expect(yAxis.scale).toBe(true);
  });

  it('R11 - null weeks break the line', () => {
    expect(series[0].connectNulls).toBe(false);
    expect(series[0].data[1]).toBeNull();
    expect(series[0].data).not.toContain(0);
  });

  it('R11 - tooltip lines incl. empty week', () => {
    expect(weekTooltipLines(weeks[0])).toEqual(['Week of 27 Apr', 'VO2max 50.0 · 3 runs']);
    expect(weekTooltipLines(weeks[1])).toEqual(['Week of 4 May', 'No qualifying runs']);
    expect(weekTooltipLines({ ...weeks[26], runCount: 1 })[0]).toBe('Week of 26 Oct (so far)');
    expect(weekTooltipLines({ ...weeks[26], runCount: 1 })[1]).toContain('· 1 run');
    const tooltip = options.tooltip as { trigger: string; formatter: (params: unknown) => string };
    expect(tooltip.trigger).toBe('axis');
    expect(tooltip.formatter([{ dataIndex: 1 }])).toBe('Week of 4 May<br/>No qualifying runs');
  });

  it('R11 - aria label', () => {
    expect(VO2MAX_CHART_LABEL).toBe('VO2max per week, last 6 months');
  });
});
