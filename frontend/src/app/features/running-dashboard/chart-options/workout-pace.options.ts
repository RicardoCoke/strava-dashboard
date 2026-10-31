import type { EChartsOption, ScatterSeriesOption } from 'echarts';
import { PacePoint, PaceTypeComparison } from '../../../core/api/running-dashboard.models';
import {
  baseChartStyle,
  categoryAxisStyle,
  tooltipStyle,
  valueAxisStyle,
} from '../../../shared/chart/chart-theme';
import { palette } from '../../../shared/chart/palette';
import { formatDayMonthYear } from '../../../shared/format/dates';
import { formatPace, formatPaceChange } from '../../../shared/format/pace';
import { Tone, toneForChange } from '../../../shared/format/tone';

export const OUTDOOR_SERIES = 'Outdoor';
export const TREADMILL_SERIES = 'Treadmill';
const LINE_SERIES = 'Pace line';

const OUTDOOR_SYMBOL_SIZE = 7;
const TREADMILL_SYMBOL_SIZE = 9;

/** "14 Oct 2026 · 4:13 /km", plus " · Treadmill" for treadmill runs. */
export function pacePointTooltip(point: PacePoint): string {
  const text = `${formatDayMonthYear(point.date)} · ${formatPace(point.repPaceSecondsPerKm)} /km`;
  return point.treadmill ? `${text} · Treadmill` : text;
}

/** "Last 30 days vs previous 30 days: 4:20 /km → 4:13 /km · ", or null without both windows. */
export function paceSummaryPrefix(type: PaceTypeComparison): string | null {
  if (!type.current || !type.previous) {
    return null;
  }
  const previous = formatPace(type.previous.averageRepPaceSecondsPerKm);
  const current = formatPace(type.current.averageRepPaceSecondsPerKm);
  return `Last 30 days vs previous 30 days: ${previous} /km → ${current} /km · `;
}

/** "7 s/km faster", "3 s/km slower" or "same pace". */
export function paceChangeText(type: PaceTypeComparison): string {
  return formatPaceChange(type.changeSecondsPerKm ?? 0);
}

export function paceChangeTone(type: PaceTypeComparison): Tone {
  return toneForChange(type.changeSecondsPerKm ?? 0);
}

/** The 30-day fallback when a window has no paced run. */
export function notEnoughPaceText(type: PaceTypeComparison): string {
  const missing =
    !type.current && !type.previous
      ? 'either period'
      : !type.previous
        ? 'the previous 30 days'
        : 'the last 30 days';
  return `Not enough data for a 30-day comparison: no paced runs in ${missing}`;
}

/** "2 runs without a rep pace are not shown", or null when every run has one. */
export function runsNotShownText(count: number): string | null {
  if (count <= 0) {
    return null;
  }
  return count === 1
    ? '1 run without a rep pace is not shown'
    : `${count} runs without a rep pace are not shown`;
}

interface ScatterGroup {
  points: PacePoint[];
  series: ScatterSeriesOption;
}

function scatterGroup(
  name: string,
  points: PacePoint[],
  symbol: 'circle' | 'triangle',
  color: string,
  size: number,
): ScatterGroup[] {
  if (points.length === 0) {
    return [];
  }
  const series: ScatterSeriesOption = {
    type: 'scatter',
    name,
    symbol,
    symbolSize: size,
    z: 3,
    itemStyle: { color },
    data: points.map((point) => [point.date, point.repPaceSecondsPerKm]),
  };
  return [{ points, series }];
}

/** One point per run over time, treadmill runs as triangles, joined by a thin line in date order. */
export function buildWorkoutPaceTrendOptions(type: PaceTypeComparison): EChartsOption {
  const groups = [
    ...scatterGroup(
      OUTDOOR_SERIES,
      type.points.filter((point) => !point.treadmill),
      'circle',
      palette.accent,
      OUTDOOR_SYMBOL_SIZE,
    ),
    ...scatterGroup(
      TREADMILL_SERIES,
      type.points.filter((point) => point.treadmill),
      'triangle',
      palette.treadmill,
      TREADMILL_SYMBOL_SIZE,
    ),
  ];
  const valueAxis = valueAxisStyle();
  return {
    ...baseChartStyle(),
    grid: { left: 56, right: 16, top: 48, bottom: 32 },
    legend: {
      data: groups.map((group) => String(group.series.name)),
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
        return point ? pacePointTooltip(point) : '';
      },
    },
    xAxis: { ...categoryAxisStyle(), type: 'time' },
    yAxis: {
      ...valueAxis,
      type: 'value',
      name: 'Rep pace (min/km), lower is faster',
      scale: true,
      axisLabel: { ...valueAxis.axisLabel, formatter: (value: number) => formatPace(value) },
    },
    series: [
      {
        type: 'line',
        name: LINE_SERIES,
        symbol: 'none',
        silent: true,
        z: 2,
        lineStyle: { width: 1.5, color: palette.secondary },
        data: type.points.map((point) => [point.date, point.repPaceSecondsPerKm]),
      },
      ...groups.map((group) => group.series),
    ],
  };
}
