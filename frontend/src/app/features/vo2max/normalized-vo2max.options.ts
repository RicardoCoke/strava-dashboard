import type { EChartsOption } from 'echarts';
import { NormalizedWeekVo2max } from '../../core/api/normalized-vo2max.models';
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
const COMPARISON_LINE_WIDTH = 1;
const COMPARISON_OPACITY = 0.5;
const SYMBOL_SIZE = 6;

export const NORMALIZED_SERIES_NAME = 'Normalized Vo2';
export const ORIGINAL_SERIES_NAME = 'Vo2';
export const NORMALIZED_VO2MAX_CHART_LABEL = 'Normalized VO2max per week, last 6 months';

/** "Week of 27 Apr", the normalized line, then the original Vo2 line. Dates and numbers only. */
export function normalizedWeekTooltipLines(week: NormalizedWeekVo2max): string[] {
  const heading = `Week of ${formatDayMonth(week.weekStart)}${week.inProgress ? ' (so far)' : ''}`;
  const normalized =
    week.vo2max === null
      ? 'No qualifying runs'
      : `Normalized VO2max ${formatOneDecimal(week.vo2max)} · ${runs(week.runCount)}`;
  const original =
    week.originalVo2max === null
      ? 'Vo2: no qualifying runs'
      : `Vo2 ${formatOneDecimal(week.originalVo2max)}`;
  return [heading, normalized, original];
}

export function buildNormalizedVo2maxOptions(weeks: NormalizedWeekVo2max[]): EChartsOption {
  return {
    ...baseChartStyle(),
    grid: { left: 48, right: 16, top: 48, bottom: 32 },
    legend: {
      data: [NORMALIZED_SERIES_NAME, ORIGINAL_SERIES_NAME],
      textStyle: { color: palette.textMuted },
      top: 0,
      right: 0,
    },
    tooltip: {
      ...tooltipStyle(),
      trigger: 'axis',
      formatter: (params) => {
        const first = (Array.isArray(params) ? params[0] : params) as { dataIndex: number };
        return normalizedWeekTooltipLines(weeks[first.dataIndex]).join('<br/>');
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
        name: NORMALIZED_SERIES_NAME,
        z: 3,
        showSymbol: true,
        symbolSize: SYMBOL_SIZE,
        connectNulls: false,
        lineStyle: { width: LINE_WIDTH, color: palette.accent },
        itemStyle: { color: palette.accent },
        data: weeks.map((week) => week.vo2max),
      },
      {
        type: 'line',
        name: ORIGINAL_SERIES_NAME,
        z: 2,
        showSymbol: false,
        connectNulls: false,
        lineStyle: {
          width: COMPARISON_LINE_WIDTH,
          color: palette.textMuted,
          opacity: COMPARISON_OPACITY,
        },
        itemStyle: { color: palette.textMuted },
        data: weeks.map((week) => week.originalVo2max),
      },
    ],
  };
}
