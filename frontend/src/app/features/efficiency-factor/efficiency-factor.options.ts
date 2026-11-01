import type { EChartsOption, ScatterSeriesOption } from 'echarts';
import { EfPoint, EfRunType } from '../../core/api/efficiency-factor.models';
import {
  baseChartStyle,
  categoryAxisStyle,
  tooltipStyle,
  valueAxisStyle,
} from '../../shared/chart/chart-theme';
import { palette } from '../../shared/chart/palette';
import { formatDayMonthYear } from '../../shared/format/dates';
import { formatTwoDecimals } from '../../shared/format/numbers';
import { formatPace } from '../../shared/format/pace';

export const EFFICIENCY_FACTOR_CHART_LABEL =
  'Efficiency factor per easy and long run, with a 4-week average';
export const EASY_SERIES = 'Easy';
export const LONG_SERIES = 'Long run';
export const ROLLING_SERIES = '4-week average';

const EASY_SYMBOL_SIZE = 7;
const LONG_SYMBOL_SIZE = 8;

const TYPE_LABELS: Record<EfRunType, string> = { easy: EASY_SERIES, long: LONG_SERIES };

/** "29 Oct 2026 · Long run · EF 1.62 · 22.4 km · 5:31 /km · 142 bpm · 4-week avg 1.65". */
export function efPointTooltip(point: EfPoint): string {
  const parts = [
    formatDayMonthYear(point.date),
    TYPE_LABELS[point.runType],
    `EF ${formatTwoDecimals(point.efficiency)}`,
    `${point.distanceKm.toFixed(1)} km`,
    `${formatPace(point.paceSecondsPerKm)} /km`,
    `${Math.round(point.averageHeartRateBpm)} bpm`,
  ];
  if (point.rollingEfficiency !== null) {
    parts.push(`4-week avg ${formatTwoDecimals(point.rollingEfficiency)}`);
  }
  return parts.join(' · ');
}

interface ScatterGroup {
  points: EfPoint[];
  series: ScatterSeriesOption;
}

function scatterGroup(
  points: EfPoint[],
  runType: EfRunType,
  color: string,
  size: number,
): ScatterGroup[] {
  const typed = points.filter((point) => point.runType === runType);
  if (typed.length === 0) {
    return [];
  }
  const series: ScatterSeriesOption = {
    type: 'scatter',
    name: TYPE_LABELS[runType],
    symbol: 'circle',
    symbolSize: size,
    z: 3,
    itemStyle: { color },
    data: typed.map((point) => [point.date, point.efficiency]),
  };
  return [{ points: typed, series }];
}

/** One point per run over time, easy and long runs in their own colours, with a 4-week rolling line. */
export function buildEfficiencyFactorOptions(points: EfPoint[]): EChartsOption {
  const groups = [
    ...scatterGroup(points, 'easy', palette.accent, EASY_SYMBOL_SIZE),
    ...scatterGroup(points, 'long', palette.longRun, LONG_SYMBOL_SIZE),
  ];
  const hasRolling = points.some((point) => point.rollingEfficiency !== null);
  const valueAxis = valueAxisStyle();
  return {
    ...baseChartStyle(),
    grid: { left: 56, right: 16, top: 48, bottom: 32 },
    legend: {
      data: [
        ...(hasRolling ? [ROLLING_SERIES] : []),
        ...groups.map((group) => String(group.series.name)),
      ],
      top: 0,
      textStyle: { color: palette.textMuted },
    },
    tooltip: {
      ...tooltipStyle(),
      trigger: 'item',
      formatter: (params) => {
        const { seriesIndex, dataIndex } = params as { seriesIndex: number; dataIndex: number };
        // Series 0 is the silent line; the scatter groups follow in order.
        const point = groups[seriesIndex - 1]?.points[dataIndex];
        return point ? efPointTooltip(point) : '';
      },
    },
    xAxis: { ...categoryAxisStyle(), type: 'time' },
    yAxis: {
      ...valueAxis,
      type: 'value',
      name: 'EF (m/min per bpm), higher is better',
      scale: true,
      axisLabel: { ...valueAxis.axisLabel, formatter: (value: number) => formatTwoDecimals(value) },
    },
    series: [
      {
        type: 'line',
        name: ROLLING_SERIES,
        symbol: 'none',
        silent: true,
        connectNulls: false,
        z: 2,
        itemStyle: { color: palette.text },
        lineStyle: { width: 2, color: palette.text },
        data: points.map((point) => [point.date, point.rollingEfficiency]),
      },
      ...groups.map((group) => group.series),
    ],
  };
}
