import { palette } from '../../shared/chart/palette';
import { trainingLoad } from '../../testing/training-load-fixtures';
import {
  TRAINING_LOAD_CHART_LABEL,
  buildTrainingLoadOptions,
  dayTooltipLines,
} from './training-load.options';

interface LineSeries {
  type: string;
  name: string;
  showSymbol: boolean;
  lineStyle: { color: string };
  data: number[];
  markLine?: {
    lineStyle: { type: string; color: string };
    label: { formatter: string };
    data: { yAxis: number }[];
  };
}

describe('buildTrainingLoadOptions', () => {
  const data = trainingLoad();
  const options = buildTrainingLoadOptions(data);
  const series = options.series as LineSeries[];

  it('R11 - three named series in palette colours', () => {
    expect(series.map((s) => s.name)).toEqual(['Fitness (42-day)', 'Fatigue (7-day)', 'Form']);
    expect(series.map((s) => s.lineStyle.color)).toEqual([
      palette.fitness,
      palette.fatigue,
      palette.form,
    ]);
    series.forEach((s) => {
      expect(s.type).toBe('line');
      expect(s.showSymbol).toBe(false);
      expect(s.data.length).toBe(185);
    });
    const xAxis = options.xAxis as { type: string; data: string[] };
    expect(xAxis.type).toBe('category');
    expect(xAxis.data[0]).toBe('30 Apr');
    const yAxis = options.yAxis as { name: string; min?: number };
    expect(yAxis.name).toBe('Load / day');
    expect(yAxis.min).toBeUndefined();
  });

  it('R11 - zero markLine on form', () => {
    const markLine = series[2].markLine;
    expect(markLine?.data).toEqual([{ yAxis: 0 }]);
    expect(markLine?.lineStyle.type).toBe('dashed');
    expect(markLine?.lineStyle.color).toBe(palette.textMuted);
    expect(markLine?.label.formatter).toBe('0');
    expect(series[0].markLine).toBeUndefined();
  });

  it('R11 - legend', () => {
    const legend = options.legend as { top: number; data: string[] };
    expect(legend.top).toBe(0);
    expect(legend.data).toEqual(['Fitness (42-day)', 'Fatigue (7-day)', 'Form']);
  });

  it('R11 - tooltip lines', () => {
    const tooltip = options.tooltip as {
      trigger: string;
      formatter: (params: unknown) => string;
    };
    expect(tooltip.trigger).toBe('axis');
    expect(dayTooltipLines(data.days[184])).toEqual([
      'Sat 31 Oct',
      'Fitness 52.1',
      'Fatigue 61.3',
      'Form -9.2',
      'Load 0.0',
    ]);
    expect(tooltip.formatter([{ dataIndex: 184 }])).toBe(
      'Sat 31 Oct<br/>Fitness 52.1<br/>Fatigue 61.3<br/>Form -9.2<br/>Load 0.0',
    );
  });

  it('R11 - aria label', () => {
    expect(TRAINING_LOAD_CHART_LABEL).toBe(
      'Training load: fitness, fatigue and form per day, last 6 months',
    );
  });
});
