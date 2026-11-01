import type { EChartsOption } from 'echarts';
import { DriftPoint, DriftThresholds, DriftZone } from '../../core/api/long-run-drift.models';
import { baseChartStyle, tooltipStyle, valueAxisStyle } from '../../shared/chart/chart-theme';
import { palette } from '../../shared/chart/palette';
import { formatDayMonthYear } from '../../shared/format/dates';
import { formatOneDecimal } from '../../shared/format/numbers';
import { formatSignedPct, zoneLabel } from './long-run-drift.view';

const LINE_WIDTH = 1;
const SYMBOL_SIZE = 9;
const BAND_OPACITY = 0.08;
const MIN_Y = -5;
const MAX_Y = 15;

export const LONG_RUN_DRIFT_CHART_LABEL = 'Heart-rate drift per long run, last 5 months';

const ZONE_COLOURS: Record<DriftZone, string> = {
  good: palette.accent,
  watch: palette.caution,
  poor: palette.worse,
};

/** "25 Oct 2026 · Drift +3.8% (Good) · 28.1 km". Numbers and dates only. */
export function driftTooltip(point: DriftPoint): string {
  return (
    `${formatDayMonthYear(point.date)} · Drift ${formatSignedPct(point.driftPct)}% ` +
    `(${zoneLabel(point.zone)}) · ${formatOneDecimal(point.distanceKm)} km`
  );
}

/** At least −5 to 15, widened to one point past the data. */
export function driftAxisBounds(points: DriftPoint[]): { min: number; max: number } {
  const values = points.map((point) => point.driftPct);
  if (values.length === 0) {
    return { min: MIN_Y, max: MAX_Y };
  }
  return {
    min: Math.min(MIN_Y, Math.floor(Math.min(...values)) - 1),
    max: Math.max(MAX_Y, Math.ceil(Math.max(...values)) + 1),
  };
}

function band(from: number, to: number, color: string) {
  return [{ yAxis: from, itemStyle: { color, opacity: BAND_OPACITY } }, { yAxis: to }];
}

export function buildLongRunDriftOptions(
  points: DriftPoint[],
  thresholds: DriftThresholds,
): EChartsOption {
  const { min, max } = driftAxisBounds(points);
  return {
    ...baseChartStyle(),
    grid: { left: 48, right: 16, top: 40, bottom: 32 },
    tooltip: {
      ...tooltipStyle(),
      trigger: 'item',
      formatter: (params) => {
        const item = (Array.isArray(params) ? params[0] : params) as { dataIndex: number };
        return driftTooltip(points[item.dataIndex]);
      },
    },
    xAxis: {
      ...valueAxisStyle(),
      type: 'time',
      splitLine: { show: false },
    },
    yAxis: { ...valueAxisStyle(), type: 'value', name: 'Drift (%)', min, max },
    series: [
      {
        type: 'line',
        name: 'Drift',
        symbol: 'circle',
        symbolSize: SYMBOL_SIZE,
        showSymbol: true,
        lineStyle: { width: LINE_WIDTH, color: palette.textMuted },
        data: points.map((point) => ({
          value: [point.date, point.driftPct],
          itemStyle: { color: ZONE_COLOURS[point.zone] },
        })),
        markArea: {
          silent: true,
          data: [
            band(min, thresholds.goodBelowPct, palette.accent),
            band(thresholds.goodBelowPct, thresholds.poorAbovePct, palette.caution),
            band(thresholds.poorAbovePct, max, palette.worse),
          ] as never,
        },
      },
    ],
  };
}
