import type { EChartsOption } from 'echarts';
import { LoadDay, TrainingLoadResponse } from '../../core/api/training-load.models';
import {
  baseChartStyle,
  categoryAxisStyle,
  tooltipStyle,
  valueAxisStyle,
} from '../../shared/chart/chart-theme';
import { palette } from '../../shared/chart/palette';
import { formatDayMonth, formatShortDate } from '../../shared/format/dates';
import { formatOneDecimal, formatSignedOneDecimal } from '../../shared/format/numbers';

const LINE_WIDTH = 2;

export const TRAINING_LOAD_CHART_LABEL =
  'Training load: fitness, fatigue and form per day, last 6 months';

/** "Sat 31 Oct", "Fitness 52.1", "Fatigue 61.3", "Form -9.2", "Load 0.0". Dates and numbers only. */
export function dayTooltipLines(day: LoadDay): string[] {
  return [
    formatShortDate(day.date),
    `Fitness ${formatOneDecimal(day.fitness)}`,
    `Fatigue ${formatOneDecimal(day.fatigue)}`,
    `Form ${formatSignedOneDecimal(day.form)}`,
    `Load ${formatOneDecimal(day.load)}`,
  ];
}

function lineSeries(name: string, color: string, values: number[]) {
  return {
    type: 'line' as const,
    name,
    showSymbol: false,
    lineStyle: { width: LINE_WIDTH, color },
    itemStyle: { color },
    data: values,
  };
}

export function buildTrainingLoadOptions(data: TrainingLoadResponse): EChartsOption {
  const days = data.days;
  return {
    ...baseChartStyle(),
    grid: { left: 48, right: 16, top: 56, bottom: 32 },
    legend: {
      top: 0,
      data: ['Fitness (42-day)', 'Fatigue (7-day)', 'Form'],
      textStyle: { color: palette.textMuted },
    },
    tooltip: {
      ...tooltipStyle(),
      trigger: 'axis',
      formatter: (params) => {
        const first = (Array.isArray(params) ? params[0] : params) as { dataIndex: number };
        return dayTooltipLines(days[first.dataIndex]).join('<br/>');
      },
    },
    xAxis: {
      ...categoryAxisStyle(),
      type: 'category',
      data: days.map((day) => formatDayMonth(day.date)),
    },
    yAxis: { ...valueAxisStyle(), type: 'value', name: 'Load / day' },
    series: [
      lineSeries(
        'Fitness (42-day)',
        palette.fitness,
        days.map((day) => day.fitness),
      ),
      lineSeries(
        'Fatigue (7-day)',
        palette.fatigue,
        days.map((day) => day.fatigue),
      ),
      {
        ...lineSeries(
          'Form',
          palette.form,
          days.map((day) => day.form),
        ),
        markLine: {
          symbol: 'none',
          silent: true,
          lineStyle: { type: 'dashed' as const, width: 1, color: palette.textMuted },
          label: { formatter: '0', color: palette.textMuted },
          data: [{ yAxis: 0 }],
        },
      },
    ],
  };
}
