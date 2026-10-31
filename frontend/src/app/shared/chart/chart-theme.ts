import { palette } from './palette';

// Shared dark styling spread into every chart's options: no axis lines or ticks, faint dashed grid, dark tooltip.

export function baseChartStyle() {
  return {
    backgroundColor: 'transparent',
    textStyle: { color: palette.textMuted },
  };
}

export function categoryAxisStyle() {
  return {
    axisLine: { show: false },
    axisTick: { show: false },
    axisLabel: { color: palette.textMuted },
    splitLine: { show: false },
  };
}

export function valueAxisStyle() {
  return {
    axisLine: { show: false },
    axisTick: { show: false },
    axisLabel: { color: palette.textMuted },
    nameTextStyle: { color: palette.textMuted },
    splitLine: { lineStyle: { color: palette.grid, width: 1, type: 'dashed' as const } },
  };
}

export function tooltipStyle() {
  return {
    backgroundColor: palette.surfaceRaised,
    borderColor: palette.border,
    textStyle: { color: palette.text },
  };
}
