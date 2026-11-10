import type { EChartsOption } from 'echarts';
import { RecoveryZone, RingsHistoryDay } from '../../core/api/rings.models';
import {
  baseChartStyle,
  categoryAxisStyle,
  tooltipStyle,
  valueAxisStyle,
} from '../../shared/chart/chart-theme';
import { palette } from '../../shared/chart/palette';
import { formatDayMonth, formatShortDate } from '../../shared/format/dates';
import { formatOneDecimal } from '../../shared/format/numbers';

export type HistoryMetric = 'sleep' | 'recovery' | 'strain';

const LINE_WIDTH = 2;
const PERCENT_MAX = 100;
const STRAIN_MAX = 21;

const ZONE_COLOURS: Record<RecoveryZone, string> = {
  green: palette.recoveryGreen,
  yellow: palette.recoveryYellow,
  red: palette.recoveryRed,
};

const LABELS: Record<HistoryMetric, string> = {
  sleep: 'Sleep performance',
  recovery: 'Recovery score',
  strain: 'Strain',
};

export function historyChartLabel(metric: HistoryMetric, range: number): string {
  return `${LABELS[metric]} per day, last ${range} days`;
}

export function isEmptySeries(values: (number | null)[]): boolean {
  return values.every((value) => value === null);
}

/** "Sleep 82%", "Recovery 75% (green)", "Strain 12.9" or "Sleep no data". Dates and numbers only. */
export function historyTooltipLine(metric: HistoryMetric, day: RingsHistoryDay): string {
  switch (metric) {
    case 'sleep':
      return day.sleepPercent === null ? 'Sleep no data' : `Sleep ${day.sleepPercent}%`;
    case 'recovery':
      return day.recoveryPercent === null
        ? 'Recovery no data'
        : `Recovery ${day.recoveryPercent}% (${day.recoveryZone})`;
    case 'strain':
      return day.strain === null ? 'Strain no data' : `Strain ${formatOneDecimal(day.strain)}`;
  }
}

function frame(
  days: RingsHistoryDay[],
  metric: HistoryMetric,
  yAxis: { name: string; max: number },
): EChartsOption {
  return {
    ...baseChartStyle(),
    grid: { left: 48, right: 16, top: 40, bottom: 32 },
    tooltip: {
      ...tooltipStyle(),
      trigger: 'axis',
      formatter: (params) => {
        const first = (Array.isArray(params) ? params[0] : params) as { dataIndex: number };
        const day = days[first.dataIndex];
        return `${formatShortDate(day.date)}<br/>${historyTooltipLine(metric, day)}`;
      },
    },
    xAxis: {
      ...categoryAxisStyle(),
      type: 'category',
      data: days.map((day) => formatDayMonth(day.date)),
    },
    yAxis: { ...valueAxisStyle(), type: 'value', min: 0, ...yAxis },
  };
}

/** Nulls stay null and `connectNulls` is off, so a missing day is a gap; symbols keep a lone day visible. */
function line(name: string, color: string, values: (number | null)[]) {
  return {
    type: 'line' as const,
    name,
    showSymbol: true,
    connectNulls: false,
    lineStyle: { width: LINE_WIDTH, color },
    itemStyle: { color },
    data: values,
  };
}

export function buildSleepHistoryOptions(days: RingsHistoryDay[]): EChartsOption {
  const name = 'Sleep (%)';
  return {
    ...frame(days, 'sleep', { name, max: PERCENT_MAX }),
    series: [
      line(
        name,
        palette.sleep,
        days.map((day) => day.sleepPercent),
      ),
    ],
  };
}

/** Bars coloured by the backend's zone, not recomputed here. */
export function buildRecoveryHistoryOptions(days: RingsHistoryDay[]): EChartsOption {
  const name = 'Recovery (%)';
  return {
    ...frame(days, 'recovery', { name, max: PERCENT_MAX }),
    series: [
      {
        type: 'bar',
        name,
        data: days.map((day) =>
          day.recoveryPercent === null || day.recoveryZone === null
            ? null
            : { value: day.recoveryPercent, itemStyle: { color: ZONE_COLOURS[day.recoveryZone] } },
        ),
      },
    ],
  };
}

export function buildStrainHistoryOptions(days: RingsHistoryDay[]): EChartsOption {
  const name = 'Strain (0–21)';
  return {
    ...frame(days, 'strain', { name, max: STRAIN_MAX }),
    series: [
      line(
        name,
        palette.strain,
        days.map((day) => day.strain),
      ),
    ],
  };
}
