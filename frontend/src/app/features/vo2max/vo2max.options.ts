import type { EChartsOption } from 'echarts';
import { WeekVo2max } from '../../core/api/vo2max.models';
import {
  baseChartStyle,
  categoryAxisStyle,
  tooltipStyle,
  valueAxisStyle,
} from '../../shared/chart/chart-theme';
import { palette } from '../../shared/chart/palette';
import { formatDayMonth } from '../../shared/format/dates';
import { formatOneDecimal } from '../../shared/format/numbers';
import { runs } from './vo2max.view';

const LINE_WIDTH = 2;
const SYMBOL_SIZE = 6;

export const VO2MAX_CHART_LABEL = 'VO2max per week, last 6 months';

/** "Week of 27 Apr", then "VO2max 52.3 · 3 runs" or "No qualifying runs". Dates and numbers only. */
export function weekTooltipLines(week: WeekVo2max): string[] {
  const heading = `Week of ${formatDayMonth(week.weekStart)}${week.inProgress ? ' (so far)' : ''}`;
  const value =
    week.vo2max === null
      ? 'No qualifying runs'
      : `VO2max ${formatOneDecimal(week.vo2max)} · ${runs(week.runCount)}`;
  return [heading, value];
}

export function buildVo2maxOptions(weeks: WeekVo2max[]): EChartsOption {
  return {
    ...baseChartStyle(),
    grid: { left: 48, right: 16, top: 40, bottom: 32 },
    tooltip: {
      ...tooltipStyle(),
      trigger: 'axis',
      formatter: (params) => {
        const first = (Array.isArray(params) ? params[0] : params) as { dataIndex: number };
        return weekTooltipLines(weeks[first.dataIndex]).join('<br/>');
      },
    },
    xAxis: {
      ...categoryAxisStyle(),
      type: 'category',
      data: weeks.map((week) => formatDayMonth(week.weekStart)),
    },
    yAxis: { ...valueAxisStyle(), type: 'value', name: 'VO2max (ml/kg/min)', scale: true },
    series: [
      {
        type: 'line',
        name: 'VO2max',
        showSymbol: true,
        symbolSize: SYMBOL_SIZE,
        connectNulls: false,
        lineStyle: { width: LINE_WIDTH, color: palette.accent },
        itemStyle: { color: palette.accent },
        data: weeks.map((week) => week.vo2max),
      },
    ],
  };
}
