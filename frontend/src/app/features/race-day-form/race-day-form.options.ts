import type { EChartsOption, LineSeriesOption } from 'echarts';
import { FormDay, RaceDayFormResponse } from '../../core/api/race-day-form.models';
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
export const FITNESS_SERIES = 'Fitness (42-day)';
export const FATIGUE_SERIES = 'Fatigue (7-day)';
export const FORM_SERIES = 'Form';
export const BAND_SERIES = 'Ready band (rough guide)';

export const RACE_DAY_FORM_CHART_LABEL =
  'Race-day form: fitness, fatigue and form from 6 weeks ago to race day; planned days dashed';

/** "Sat 7 Nov · planned" (or "Sat 31 Oct"), then fitness, fatigue, form, load and band. Dates and numbers only. */
export function formDayTooltipLines(day: FormDay): string[] {
  const date = formatShortDate(day.date);
  return [
    day.kind === 'planned' ? `${date} · planned` : date,
    `Fitness ${formatOneDecimal(day.fitness)}`,
    `Fatigue ${formatOneDecimal(day.fatigue)}`,
    `Form ${formatSignedOneDecimal(day.form)}`,
    `Load ${formatOneDecimal(day.load)}`,
    `Ready band ${formatOneDecimal(day.readyLow)}–${formatOneDecimal(day.readyHigh)}`,
  ];
}

type Metric = (day: FormDay) => number;

/**
 * Two series per metric with the same name: the actual one (solid, values up to [lastActual]) and the planned
 * one (dashed, from [lastActual] on, sharing that point so the line is continuous). The legend toggles both.
 */
function metricSeries(
  name: string,
  color: string,
  days: FormDay[],
  lastActual: number,
  value: Metric,
  projected: boolean,
): LineSeriesOption[] {
  const line = (type: 'solid' | 'dashed', data: (number | null)[]): LineSeriesOption => ({
    type: 'line',
    name,
    showSymbol: false,
    z: 3,
    lineStyle: { width: LINE_WIDTH, color, type },
    itemStyle: { color },
    data,
  });
  const actual = line(
    'solid',
    days.map((day, i) => (i <= lastActual ? value(day) : null)),
  );
  const planned = line(
    'dashed',
    days.map((day, i) => (i >= lastActual ? value(day) : null)),
  );
  return projected ? [actual, planned] : [actual];
}

/** A hidden lower edge and the band on top of it, stacked, under the metric lines. */
function bandSeries(days: FormDay[]): LineSeriesOption[] {
  const base = {
    type: 'line' as const,
    stack: 'ready',
    showSymbol: false,
    z: 1,
    lineStyle: { width: 0 },
  };
  return [
    { ...base, name: 'Ready band low', silent: true, data: days.map((day) => day.readyLow) },
    {
      ...base,
      name: BAND_SERIES,
      areaStyle: { color: palette.readyBand },
      itemStyle: { color: palette.readyBand },
      data: days.map((day) => day.readyHigh - day.readyLow),
    },
  ];
}

export function buildRaceDayFormOptions(data: RaceDayFormResponse): EChartsOption {
  const days = data.days;
  const lastActual = Math.max(
    0,
    days.findIndex((day) => day.date === data.lastActualDate),
  );
  const projected = data.projection === 'projected';
  const [fitnessActual, ...fitnessRest] = metricSeries(
    FITNESS_SERIES,
    palette.fitness,
    days,
    lastActual,
    (day) => day.fitness,
    projected,
  );
  const raceDayInChart = data.raceDay !== null && days.some((day) => day.date === data.raceDate);
  const fitnessWithMarker: LineSeriesOption = raceDayInChart
    ? {
        ...fitnessActual,
        markLine: {
          symbol: 'none',
          silent: true,
          lineStyle: { type: 'solid', width: 1, color: palette.accent },
          label: { formatter: 'Race day', position: 'end', color: palette.accent },
          data: [{ xAxis: formatDayMonth(data.raceDate) }],
        },
      }
    : fitnessActual;
  return {
    ...baseChartStyle(),
    grid: { left: 48, right: 16, top: 64, bottom: 32 },
    legend: {
      top: 0,
      data: [FITNESS_SERIES, FATIGUE_SERIES, FORM_SERIES, BAND_SERIES],
      textStyle: { color: palette.textMuted },
    },
    tooltip: {
      ...tooltipStyle(),
      trigger: 'axis',
      formatter: (params) => {
        const first = (Array.isArray(params) ? params[0] : params) as { dataIndex: number };
        return formDayTooltipLines(days[first.dataIndex]).join('<br/>');
      },
    },
    xAxis: {
      ...categoryAxisStyle(),
      type: 'category',
      data: days.map((day) => formatDayMonth(day.date)),
    },
    yAxis: { ...valueAxisStyle(), type: 'value', name: 'Load / day' },
    series: [
      ...bandSeries(days),
      fitnessWithMarker,
      ...fitnessRest,
      ...metricSeries(
        FATIGUE_SERIES,
        palette.fatigue,
        days,
        lastActual,
        (day) => day.fatigue,
        projected,
      ),
      ...metricSeries(FORM_SERIES, palette.form, days, lastActual, (day) => day.form, projected),
    ],
  };
}
