import { palette } from '../../shared/chart/palette';
import { driftPoint } from '../../testing/long-run-drift-fixtures';
import { buildLongRunDriftOptions, driftAxisBounds, driftTooltip } from './long-run-drift.options';

const thresholds = { goodBelowPct: 5, poorAbovePct: 10 };

interface Band {
  yAxis: number;
  itemStyle?: { color: string; opacity: number };
}

interface LineSeries {
  symbol: string;
  lineStyle: { color: string };
  data: { value: [string, number]; itemStyle: { color: string } }[];
  markArea: { data: [Band, Band][] };
}

function series(points = [driftPoint()]): LineSeries {
  const options = buildLongRunDriftOptions(points, thresholds);
  return (options.series as unknown as LineSeries[])[0];
}

describe('buildLongRunDriftOptions', () => {
  it('R7 - y axis bounds', () => {
    expect(driftAxisBounds([driftPoint({ driftPct: 3 })])).toEqual({ min: -5, max: 15 });
    expect(
      driftAxisBounds([driftPoint({ driftPct: -7.3 }), driftPoint({ driftPct: 18.2 })]),
    ).toEqual({ min: -9, max: 20 });
    const options = buildLongRunDriftOptions([driftPoint()], thresholds);
    expect(options.yAxis).toEqual(expect.objectContaining({ name: 'Drift (%)', min: -5, max: 15 }));
    expect(options.xAxis).toEqual(expect.objectContaining({ type: 'time' }));
  });

  it('R7 - zone bands', () => {
    const bands = series().markArea.data;
    expect(bands.map(([from, to]) => [from.yAxis, to.yAxis])).toEqual([
      [-5, 5],
      [5, 10],
      [10, 15],
    ]);
    expect(bands.map(([from]) => from.itemStyle)).toEqual([
      { color: palette.accent, opacity: 0.08 },
      { color: palette.caution, opacity: 0.08 },
      { color: palette.worse, opacity: 0.08 },
    ]);
  });

  it('R7 - points coloured by zone', () => {
    const line = series([
      driftPoint({ date: '2026-06-07', driftPct: 3, zone: 'good' }),
      driftPoint({ date: '2026-07-12', driftPct: 7, zone: 'watch' }),
      driftPoint({ date: '2026-08-02', driftPct: 12, zone: 'poor' }),
    ]);
    expect(line.symbol).toBe('circle');
    expect(line.lineStyle.color).toBe(palette.textMuted);
    expect(line.data.map((item) => item.itemStyle.color)).toEqual([
      palette.accent,
      palette.caution,
      palette.worse,
    ]);
    expect(line.data[1].value).toEqual(['2026-07-12', 7]);
  });

  it('R7 - tooltip text', () => {
    expect(driftTooltip(driftPoint())).toBe('25 Oct 2026 · Drift +3.8% (Good) · 28.1 km');
    expect(driftTooltip(driftPoint({ driftPct: -1.2, zone: 'good', distanceKm: 30 }))).toBe(
      '25 Oct 2026 · Drift −1.2% (Good) · 30.0 km',
    );
  });

  it('R7 - single point renders', () => {
    const line = series([driftPoint()]);
    expect(line.data.length).toBe(1);
    expect(line.markArea.data.length).toBe(3);
  });
});
