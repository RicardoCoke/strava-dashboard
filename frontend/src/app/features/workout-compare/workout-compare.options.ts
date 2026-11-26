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

/** Legend selection by run label, as ECharts reports it; a missing label is shown. */
export type ShownRuns = Record<string, boolean>;

/** The run's point nearest [x]; none once [x] is past the run's last point. */
function nearestPoint(
  points: ComparePoint[],
  x: number,
  axis: CompareAxis,
): ComparePoint | undefined {
  const last = points.at(-1);
  if (!last || x > xOf(last, axis)) {
    return undefined;
  }
  const distance = (point: ComparePoint) => Math.abs(xOf(point, axis) - x);
  return points.reduce((best, point) => (distance(point) < distance(best) ? point : best));
}

/**
 * The hovered x, then one line per shown run at its nearest point, e.g. "Earlier · 10 Nov: 4:10 /km · 152 bpm".
 * ECharts' axis params hold only the series nearest the pointer, and the runs' x values rarely match, so each run's
 * point is looked up here. Labels and numbers only.
 */
export function compareTooltipLines(
  x: number,
  data: WorkoutCompareResponse,
  axis: CompareAxis,
  shown: ShownRuns = {},
): string[] {
  const labels = runLabels(data);
  const lines = data.runs.flatMap((run, i) => {
    const point = nearestPoint(run.points, x, axis);
    if (!point || shown[labels[i]] === false) {
      return [];
    }
    const pace = point.paceSecondsPerKm === null ? '—' : formatPace(point.paceSecondsPerKm);
    const heartRate = point.heartRateBpm === null ? '' : ` · ${point.heartRateBpm} bpm`;
    return [`${labels[i]}: ${pace} /km${heartRate}`];
  });
  const head =
    axis === 'distance' ? `${x.toFixed(2)} km` : formatRaceTime(Math.round(x * SECONDS_PER_MINUTE));
  return lines.length > 0 ? [head, ...lines] : [];
}

function axisValueOf(params: unknown): number | undefined {
  const first = (Array.isArray(params) ? params[0] : params) as { axisValue?: unknown } | undefined;
  return typeof first?.axisValue === 'number' ? first.axisValue : undefined;
}

/**
 * Pace solid on the inverted left axis, HR dashed on the right; both series of a run share its legend entry.
 * [shown] is the page's copy of the legend selection, so rebuilt options (an axis switch) keep it.
 */
export function buildCompareOptions(
  data: WorkoutCompareResponse,
  axis: CompareAxis,
  shown: ShownRuns = {},
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
    legend: { data: labels, selected: shown, top: 0, textStyle: { color: palette.textMuted } },
    tooltip: {
      ...tooltipStyle(),
      trigger: 'axis',
      formatter: (params) => {
        const x = axisValueOf(params);
        return x === undefined ? '' : compareTooltipLines(x, data, axis, shown).join('<br/>');
      },
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
