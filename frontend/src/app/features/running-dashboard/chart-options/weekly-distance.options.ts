import type { EChartsOption } from 'echarts';
import { WeekTotal } from '../../../core/api/running-dashboard.models';
import {
  baseChartStyle,
  categoryAxisStyle,
  tooltipStyle,
  valueAxisStyle,
} from '../../../shared/chart/chart-theme';
import { palette } from '../../../shared/chart/palette';
import { formatDayMonth } from '../../../shared/format/dates';

const BAR_MAX_WIDTH = 28;
const ROUNDED_TOP: [number, number, number, number] = [4, 4, 0, 0];

/** "31 Aug – 6 Sep: 32.4 km · 4 runs", plus " (this week)" for the current week. */
export function weekTooltip(week: WeekTotal): string {
  const runs = `${week.runCount} ${week.runCount === 1 ? 'run' : 'runs'}`;
  const text = `${week.label} – ${formatDayMonth(week.weekEnd)}: ${week.distanceKm.toFixed(1)} km · ${runs}`;
  return week.isCurrentWeek ? `${text} (this week)` : text;
}

export function buildWeeklyDistanceOptions(weeks: WeekTotal[]): EChartsOption {
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
        name: 'Distance',
        barMaxWidth: BAR_MAX_WIDTH,
        data: weeks.map((week) => ({
          value: week.distanceKm,
          itemStyle: {
            color: week.isCurrentWeek ? palette.accent : palette.secondary,
            borderRadius: ROUNDED_TOP,
          },
        })),
      },
    ],
  };
}
