import type { EChartsOption } from 'echarts';
import { MarathonPaceResponse, MpWeek } from '../../core/api/marathon-pace.models';
import {
  baseChartStyle,
  categoryAxisStyle,
  tooltipStyle,
  valueAxisStyle,
} from '../../shared/chart/chart-theme';
import { palette } from '../../shared/chart/palette';
import { formatDayMonth } from '../../shared/format/dates';
import { formatOneDecimal } from '../../shared/format/numbers';

const BAR_MAX_WIDTH = 28;
const ROUNDED_TOP: [number, number, number, number] = [4, 4, 0, 0];

export const MARATHON_PACE_CHART_LABEL = 'Marathon-pace km per week of the build';

/** "Week 3 · 31 Aug – 6 Sep: 4.2 km at MP", plus " (this week)" or " (upcoming)". Dates and numbers only. */
export function weekTooltip(week: MpWeek): string {
  const text =
    `Week ${week.weekIndex} · ${week.label} – ${formatDayMonth(week.weekEnd)}: ` +
    `${formatOneDecimal(week.mpKm)} km at MP`;
  if (week.isCurrentWeek) {
    return `${text} (this week)`;
  }
  return week.isFuture ? `${text} (upcoming)` : text;
}

export function buildMarathonPaceOptions(data: MarathonPaceResponse): EChartsOption {
  const weeks = data.weeks;
  return {
    ...baseChartStyle(),
    grid: { left: 48, right: 16, top: 40, bottom: 32 },
    tooltip: {
      ...tooltipStyle(),
      trigger: 'item',
      formatter: (params) => weekTooltip(weeks[(params as { dataIndex: number }).dataIndex]),
    },
    xAxis: { ...categoryAxisStyle(), type: 'category', data: weeks.map((week) => week.label) },
    yAxis: { ...valueAxisStyle(), type: 'value', name: 'km' },
    series: [
      {
        type: 'bar',
        name: 'MP km',
        barMaxWidth: BAR_MAX_WIDTH,
        data: weeks.map((week) => ({
          value: week.mpKm,
          itemStyle: {
            color: week.isCurrentWeek ? palette.accent : palette.secondary,
            borderRadius: ROUNDED_TOP,
          },
          label: {
            show: !week.isFuture,
            position: 'top' as const,
            color: palette.textMuted,
            formatter: () => formatOneDecimal(week.mpKm),
          },
        })),
      },
    ],
  };
}
