import type { EChartsOption } from 'echarts';
import { PaceTypeComparison } from '../../../core/api/running-dashboard.models';
import {
  baseChartStyle,
  categoryAxisStyle,
  tooltipStyle,
  valueAxisStyle,
} from '../../../shared/chart/chart-theme';
import { palette } from '../../../shared/chart/palette';
import { formatPace } from '../../../shared/format/pace';

export const PREVIOUS_SERIES = 'Previous 30 days';
export const CURRENT_SERIES = 'Last 30 days';

const BAR_MAX_WIDTH = 28;
const ROUNDED_TOP: [number, number, number, number] = [4, 4, 0, 0];

const paceLabel = {
  show: true,
  position: 'top' as const,
  color: palette.text,
  formatter: (params: unknown) => formatPace((params as { value: number }).value),
};

/** A workout type with a pace in both windows, the two paces narrowed to numbers. */
export interface ComparablePaceType {
  type: PaceTypeComparison;
  current: number;
  previous: number;
}

/** The one rule for which types can be compared (and drawn): a paced run in both windows. */
export function comparablePaceTypes(types: PaceTypeComparison[]): ComparablePaceType[] {
  return types.flatMap((type) =>
    type.hasEnoughData && type.current && type.previous
      ? [
          {
            type,
            current: type.current.averageRepPaceSecondsPerKm,
            previous: type.previous.averageRepPaceSecondsPerKm,
          },
        ]
      : [],
  );
}

/** Grouped bars per workout type; only comparable types are drawn. */
export function buildWorkoutPaceOptions(types: PaceTypeComparison[]): EChartsOption {
  const drawn = comparablePaceTypes(types);
  const valueAxis = valueAxisStyle();
  return {
    ...baseChartStyle(),
    grid: { left: 56, right: 16, top: 56, bottom: 32 },
    legend: {
      data: [PREVIOUS_SERIES, CURRENT_SERIES],
      top: 0,
      textStyle: { color: palette.textMuted },
    },
    tooltip: {
      ...tooltipStyle(),
      trigger: 'axis',
      valueFormatter: (value) => `${formatPace(Number(value))} /km`,
    },
    xAxis: { ...categoryAxisStyle(), type: 'category', data: drawn.map((row) => row.type.label) },
    yAxis: {
      ...valueAxis,
      type: 'value',
      name: 'Pace (min/km), lower is faster',
      scale: true,
      axisLabel: { ...valueAxis.axisLabel, formatter: (value: number) => formatPace(value) },
    },
    series: [
      {
        type: 'bar',
        name: PREVIOUS_SERIES,
        barMaxWidth: BAR_MAX_WIDTH,
        itemStyle: { color: palette.secondary, borderRadius: ROUNDED_TOP },
        label: paceLabel,
        data: drawn.map((row) => row.previous),
      },
      {
        type: 'bar',
        name: CURRENT_SERIES,
        barMaxWidth: BAR_MAX_WIDTH,
        itemStyle: { color: palette.accent, borderRadius: ROUNDED_TOP },
        label: paceLabel,
        data: drawn.map((row) => row.current),
      },
    ],
  };
}
