import type { EChartsOption } from 'echarts';
import { WellnessDay, WellnessResponse } from '../../core/api/wellness.models';
import {
  baseChartStyle,
  categoryAxisStyle,
  tooltipStyle,
  valueAxisStyle,
} from '../../shared/chart/chart-theme';
import { palette } from '../../shared/chart/palette';
import { formatDayMonth, formatShortDate } from '../../shared/format/dates';
import { formatOneDecimal } from '../../shared/format/numbers';

const LINE_WIDTH = 2;
const BASELINE_WIDTH = 1.5;
const RESTING_HR = 'Resting HR';
const RESTING_HR_BASELINE = 'Resting HR 7-day baseline';
const HRV = 'HRV';
const HRV_BASELINE = 'HRV 7-day baseline';

export const WELLNESS_CHART_LABEL =
  'Resting heart rate and HRV per day with 7-day baselines, last 12 weeks';

function metricLine(
  name: string,
  value: number | null,
  unit: string,
  baseline: number | null,
): string {
  if (value === null) {
    return `${name} no data`;
  }
  const base = baseline === null ? '' : ` (baseline ${formatOneDecimal(baseline)})`;
  return `${name} ${value} ${unit}${base}`;
}

/** "Sat 31 Oct", "Resting HR 52 bpm (baseline 48.3)", "HRV 61 ms (baseline 58.0)". Dates and numbers only. */
export function wellnessTooltipLines(day: WellnessDay): string[] {
  return [
    formatShortDate(day.date),
    metricLine(RESTING_HR, day.restingHrBpm, 'bpm', day.restingHrBaselineBpm),
    metricLine(HRV, day.hrvMs, 'ms', day.hrvBaselineMs),
  ];
}

/** Nulls stay null and `connectNulls` is off, so a missing day is a gap, never a zero. */
function line(
  name: string,
  color: string,
  yAxisIndex: number,
  values: (number | null)[],
  dashed: boolean,
) {
  return {
    type: 'line' as const,
    name,
    yAxisIndex,
    showSymbol: false,
    connectNulls: false,
    lineStyle: {
      width: dashed ? BASELINE_WIDTH : LINE_WIDTH,
      color,
      type: dashed ? ('dashed' as const) : ('solid' as const),
    },
    itemStyle: { color },
    data: values,
  };
}

export function buildWellnessOptions(data: WellnessResponse): EChartsOption {
  const days = data.days;
  return {
    ...baseChartStyle(),
    grid: { left: 48, right: 48, top: 72, bottom: 32 },
    legend: {
      top: 0,
      type: 'plain',
      data: [RESTING_HR, RESTING_HR_BASELINE, HRV, HRV_BASELINE],
      textStyle: { color: palette.textMuted },
    },
    tooltip: {
      ...tooltipStyle(),
      trigger: 'axis',
      formatter: (params) => {
        const first = (Array.isArray(params) ? params[0] : params) as { dataIndex: number };
        return wellnessTooltipLines(days[first.dataIndex]).join('<br/>');
      },
    },
    xAxis: {
      ...categoryAxisStyle(),
      type: 'category',
      data: days.map((day) => formatDayMonth(day.date)),
    },
    yAxis: [
      { ...valueAxisStyle(), type: 'value', name: 'Resting HR (bpm)', scale: true },
      {
        ...valueAxisStyle(),
        type: 'value',
        name: 'HRV (ms)',
        scale: true,
        splitLine: { show: false },
      },
    ],
    series: [
      line(
        RESTING_HR,
        palette.restingHr,
        0,
        days.map((day) => day.restingHrBpm),
        false,
      ),
      line(
        RESTING_HR_BASELINE,
        palette.restingHr,
        0,
        days.map((day) => day.restingHrBaselineBpm),
        true,
      ),
      line(
        HRV,
        palette.hrv,
        1,
        days.map((day) => day.hrvMs),
        false,
      ),
      line(
        HRV_BASELINE,
        palette.hrv,
        1,
        days.map((day) => day.hrvBaselineMs),
        true,
      ),
    ],
  };
}
