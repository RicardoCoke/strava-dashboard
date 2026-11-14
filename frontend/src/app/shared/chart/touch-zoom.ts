import type { EChartsCoreOption } from 'echarts/core';

/** Feature 024: pinch zooms the horizontal axis; one finger keeps scrolling the page. */
const TOUCH_ZOOM = {
  type: 'inside',
  xAxisIndex: 0,
  filterMode: 'none',
  start: 0,
  end: 100,
  minSpan: 5,
  zoomOnMouseWheel: false,
  moveOnMouseWheel: false,
  moveOnMouseMove: false,
  // Otherwise ECharts blocks one-finger page scroll over the plot area.
  preventDefaultMouseMove: false,
};

export function withTouchZoom(options: EChartsCoreOption, touch: boolean): EChartsCoreOption {
  return touch ? { ...options, dataZoom: [TOUCH_ZOOM] } : options;
}
