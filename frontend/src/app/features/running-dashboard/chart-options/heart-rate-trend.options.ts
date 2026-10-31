import type { EChartsOption, LineSeriesOption } from 'echarts';
import { HrPoint, HrRunType, HrTrendLine } from '../../../core/api/running-dashboard.models';
import {
  baseChartStyle,
  categoryAxisStyle,
  tooltipStyle,
  valueAxisStyle,
} from '../../../shared/chart/chart-theme';
import { palette } from '../../../shared/chart/palette';
import { formatDayMonthYear } from '../../../shared/format/dates';
import { formatBpmChange } from '../../../shared/format/heart-rate';

const POINT_GLOW_BLUR = 10;

/** A run of consecutive line segments drawn in one colour. */
export interface HrLineStretch {
  color: string;
  data: [string, number][];
}

/** "12 Oct 2026 · 142.3 bpm · 8.2 km", plus " · Treadmill" for treadmill runs. */
export function hrPointTooltip(point: HrPoint): string {
  const text = `${formatDayMonthYear(point.date)} · ${point.averageHeartRateBpm.toFixed(1)} bpm · ${point.distanceKm.toFixed(1)} km`;
  return point.treadmill ? `${text} · Treadmill` : text;
}

/** "Trend: −8.0 bpm over the period (lower = fitter)". */
export function hrTrendText(trend: HrTrendLine): string {
  return `Trend: ${formatBpmChange(trend.changeBpm)} bpm over the period (lower = fitter)`;
}

/**
 * Groups the line segments by colour: segment j−1 → j is red-orange when point j is rising,
 * accent otherwise. Neighbouring stretches share their joining point, so the line has no gaps.
 */
export function hrLineStretches(points: HrPoint[]): HrLineStretch[] {
  const stretches: HrLineStretch[] = [];
  for (let j = 1; j < points.length; j++) {
    const color = points[j].rising ? palette.worse : palette.accent;
    const end: [string, number] = [points[j].date, points[j].averageHeartRateBpm];
    const last = stretches.at(-1);
    if (last && last.color === color) {
      last.data.push(end);
    } else {
      const start: [string, number] = [points[j - 1].date, points[j - 1].averageHeartRateBpm];
      stretches.push({ color, data: [start, end] });
    }
  }
  return stretches;
}

function pointColor(point: HrPoint): string {
  if (point.treadmill) {
    return palette.treadmill;
  }
  return point.rising ? palette.worse : palette.accent;
}

function stretchSeries(stretch: HrLineStretch): LineSeriesOption {
  return {
    type: 'line',
    name: 'HR line',
    symbol: 'none',
    silent: true,
    z: 2,
    lineStyle: { width: 1.5, color: stretch.color },
    data: stretch.data,
  };
}

export function buildHeartRateTrendOptions(type: HrRunType): EChartsOption {
  const trend = type.trend;
  return {
    ...baseChartStyle(),
    grid: { left: 56, right: 16, top: 40, bottom: 32 },
    tooltip: {
      ...tooltipStyle(),
      trigger: 'item',
      formatter: (params) => {
        const { seriesIndex, dataIndex } = params as { seriesIndex: number; dataIndex: number };
        return seriesIndex === 0 ? hrPointTooltip(type.points[dataIndex]) : 'Trend';
      },
    },
    xAxis: { ...categoryAxisStyle(), type: 'time' },
    yAxis: { ...valueAxisStyle(), type: 'value', name: 'Avg HR (bpm)', scale: true },
    series: [
      {
        type: 'line',
        name: type.label,
        symbol: 'circle',
        symbolSize: 6,
        z: 3,
        // Dots only: the line is drawn by the coloured stretch series below.
        lineStyle: { width: 0 },
        data: type.points.map((point) => {
          const color = pointColor(point);
          return {
            value: [point.date, point.averageHeartRateBpm],
            itemStyle: { color, shadowBlur: POINT_GLOW_BLUR, shadowColor: color },
          };
        }),
      },
      ...(trend
        ? [
            {
              type: 'line' as const,
              name: 'Trend',
              symbol: 'none',
              lineStyle: { type: 'dashed' as const, width: 1, color: palette.textMuted },
              data: [
                [trend.startDate, trend.startBpm],
                [trend.endDate, trend.endBpm],
              ],
            },
          ]
        : []),
      ...hrLineStretches(type.points).map(stretchSeries),
    ],
  };
}
