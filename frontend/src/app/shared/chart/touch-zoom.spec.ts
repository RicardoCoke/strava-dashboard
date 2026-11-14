import { withTouchZoom } from './touch-zoom';

describe('withTouchZoom', () => {
  const options = { xAxis: { type: 'time' }, series: [{ type: 'line', data: [1, 2] }] };
  const dataZoom = () => withTouchZoom(options, true)['dataZoom'] as Record<string, unknown>[];

  it('R1 - adds one inside dataZoom on x axis 0 for touch', () => {
    expect(dataZoom().length).toBe(1);
    expect(dataZoom()[0]).toEqual(expect.objectContaining({ type: 'inside', xAxisIndex: 0 }));
    expect(dataZoom()[0]).not.toHaveProperty('yAxisIndex');
  });

  it('R2 - zoom starts at full range and is limited to 20x', () => {
    expect(dataZoom()[0]).toEqual(expect.objectContaining({ start: 0, end: 100, minSpan: 5 }));
  });

  it('R4 - disables wheel, pan and mouse-move preventDefault', () => {
    expect(dataZoom()[0]).toEqual(
      expect.objectContaining({
        moveOnMouseMove: false,
        moveOnMouseWheel: false,
        zoomOnMouseWheel: false,
        preventDefaultMouseMove: false,
      }),
    );
  });

  it('R7 - returns options unchanged when not touch', () => {
    expect(withTouchZoom(options, false)).toBe(options);
  });

  it('R8 - keeps existing keys and does not mutate input', () => {
    const result = withTouchZoom(options, true);

    expect(result['xAxis']).toBe(options.xAxis);
    expect(result['series']).toBe(options.series);
    expect(Object.keys(options)).toEqual(['xAxis', 'series']);
  });
});
