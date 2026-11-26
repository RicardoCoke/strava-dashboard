import type { EChartsOption } from 'echarts';
import { ComparePoint, WorkoutCompareResponse } from '../../core/api/workout-compare.models';
import { baseChartStyle, tooltipStyle, valueAxisStyle } from '../../shared/chart/chart-theme';
import { palette } from '../../shared/chart/palette';
import { formatRaceTime } from '../../shared/format/duration';
import { formatPace } from '../../shared/format/pace';
import { runLabels } from './workout-compare.view';

export type CompareAxis = 'distance' | 'time';

const PACE_WIDTH = 2;
const HR_WIDTH = 1.5;
const SECONDS_PER_MINUTE = 60;
const COLOURS = [palette.secondary, palette.accent] as const;

export function compareChartLabel(axis: CompareAxis): string {
  return `Pace and heart rate of two runs by ${axis}`;
}

function xOf(point: ComparePoint, axis: CompareAxis): number {
  return axis === 'distance' ? point.distanceKm : point.timeSeconds / SECONDS_PER_MINUTE;
}

interface TooltipParam {
  seriesIndex: number;
  dataIndex: number;
}

/**
 * The x value, then one line per visible run, e.g. "Earlier · 10 Nov: 4:10 /km · 152 bpm". Series 0–1 are the
 * runs' pace and 2–3 their HR, so a run is visible when either of its series is in [params]. Labels and numbers only.
 */
export function compareTooltipLines(
  params: unknown,
  data: WorkoutCompareResponse,
  axis: CompareAxis,
): string[] {
  const list = (Array.isArray(params) ? params : [params]) as TooltipParam[];
  const labels = runLabels(data);
  const points = [0, 1].map((run) => {
    const param = list.find((p) => p.seriesIndex % 2 === run);
    return param ? data.runs[run].points[param.dataIndex] : undefined;
  });
  const first = points.find((point) => point !== undefined);
  if (!first) {
    return [];
  }
  const x =
    axis === 'distance' ? `${first.distanceKm.toFixed(2)} km` : formatRaceTime(first.timeSeconds);
  const lines = points.flatMap((point, run) => {
    if (!point) {
      return [];
    }
    const pace = point.paceSecondsPerKm === null ? '—' : formatPace(point.paceSecondsPerKm);
    const heartRate = point.heartRateBpm === null ? '' : ` · ${point.heartRateBpm} bpm`;
    return [`${labels[run]}: ${pace} /km${heartRate}`];
  });
  return [x, ...lines];
}

/** Pace solid on the inverted left axis, HR dashed on the right; both series of a run share its legend entry. */
export function buildCompareOptions(
  data: WorkoutCompareResponse,
  axis: CompareAxis,
): EChartsOption {
  const labels = runLabels(data);
  const line = (run: 0 | 1, value: 'pace' | 'hr') => ({
    type: 'line' as const,
    name: labels[run],
    yAxisIndex: value === 'pace' ? 0 : 1,
    showSymbol: data.resolution === 'splits',
    connectNulls: false,
    lineStyle: {
      color: COLOURS[run],
      width: value === 'pace' ? PACE_WIDTH : HR_WIDTH,
      type: value === 'pace' ? ('solid' as const) : ('dashed' as const),
    },
    itemStyle: { color: COLOURS[run] },
    data: data.runs[run].points.map((point) => [
      xOf(point, axis),
      value === 'pace' ? point.paceSecondsPerKm : point.heartRateBpm,
    ]),
  });
  return {
    ...baseChartStyle(),
    grid: { left: 52, right: 48, top: 48, bottom: 40 },
    legend: { data: labels, top: 0, textStyle: { color: palette.textMuted } },
    tooltip: {
      ...tooltipStyle(),
      trigger: 'axis',
      formatter: (params) => compareTooltipLines(params, data, axis).join('<br/>'),
    },
    xAxis: {
      ...valueAxisStyle(),
      type: 'value',
      min: 0,
      name: axis === 'distance' ? 'Distance (km)' : 'Time (min)',
      nameLocation: 'middle',
      nameGap: 26,
    },
    yAxis: [
      {
        ...valueAxisStyle(),
        type: 'value',
        name: 'Pace (/km)',
        inverse: true,
        scale: true,
        axisLabel: { color: palette.textMuted, formatter: (value: number) => formatPace(value) },
      },
      {
        ...valueAxisStyle(),
        type: 'value',
        name: 'HR (bpm)',
        scale: true,
        splitLine: { show: false },
      },
    ],
    series: [line(0, 'pace'), line(1, 'pace'), line(0, 'hr'), line(1, 'hr')],
  };
}
