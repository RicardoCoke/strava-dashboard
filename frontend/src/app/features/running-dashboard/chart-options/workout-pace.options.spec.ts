import type { ScatterSeriesOption } from 'echarts';
import { palette } from '../../../shared/chart/palette';
import { paceType } from '../../../testing/dashboard-fixtures';
import {
  OUTDOOR_SERIES,
  TREADMILL_SERIES,
  buildWorkoutPaceTrendOptions,
  notEnoughPaceText,
  paceChangeText,
  paceChangeTone,
  paceSummaryPrefix,
  pacePointTooltip,
  runsNotShownText,
} from './workout-pace.options';

type Formatter = (params: unknown) => string;

describe('workout pace options (010)', () => {
  it('010 R5 - one point per run on a time axis with pace units', () => {
    const options = buildWorkoutPaceTrendOptions(paceType());
    const [line, ...scatters] = options.series as ScatterSeriesOption[];

    expect(scatters.flatMap((series) => series.data as unknown[])).toHaveLength(5);
    expect(line).toMatchObject({ type: 'line', symbol: 'none', silent: true });
    expect((line.data as [string, number][]).map(([date]) => date)).toEqual([
      '2026-09-05',
      '2026-09-12',
      '2026-09-20',
      '2026-10-05',
      '2026-10-20',
    ]);
    expect(options.xAxis).toMatchObject({ type: 'time' });
    expect(options.yAxis).toMatchObject({ name: 'Rep pace (min/km), lower is faster' });
    const axisFormatter = (options.yAxis as { axisLabel: { formatter: (v: number) => string } })
      .axisLabel.formatter;
    expect(axisFormatter(253)).toBe('4:13');
  });

  it('010 R5 - point tooltip shows date and pace', () => {
    const outdoor = { date: '2026-10-14', repPaceSecondsPerKm: 253, treadmill: false };
    expect(pacePointTooltip(outdoor)).toBe('14 Oct 2026 · 4:13 /km');
    expect(pacePointTooltip({ ...outdoor, treadmill: true })).toBe(
      '14 Oct 2026 · 4:13 /km · Treadmill',
    );

    const options = buildWorkoutPaceTrendOptions(paceType());
    const formatter = (options.tooltip as { formatter: Formatter }).formatter;
    // Series 2 is the treadmill scatter; its second point is 2026-10-20 at 4:12.
    expect(formatter({ seriesIndex: 2, dataIndex: 1 })).toBe('20 Oct 2026 · 4:12 /km · Treadmill');
    expect(formatter({ seriesIndex: 1, dataIndex: 0 })).toBe(
      pacePointTooltip(paceType().points[0]),
    );
  });

  it('010 R6 - treadmill points are triangles in treadmill colour', () => {
    const [, outdoor, treadmill] = buildWorkoutPaceTrendOptions(paceType())
      .series as ScatterSeriesOption[];
    expect(outdoor).toMatchObject({ name: OUTDOOR_SERIES, symbol: 'circle' });
    expect(outdoor.itemStyle).toEqual({ color: palette.accent });
    expect(outdoor.data).toHaveLength(3);
    expect(treadmill).toMatchObject({ name: TREADMILL_SERIES, symbol: 'triangle' });
    expect(treadmill.itemStyle).toEqual({ color: palette.treadmill });
    expect(treadmill.data).toEqual([
      ['2026-09-12', 259],
      ['2026-10-20', 252],
    ]);
  });

  it('010 R6 - legend only lists series with points', () => {
    expect(buildWorkoutPaceTrendOptions(paceType()).legend).toMatchObject({
      data: [OUTDOOR_SERIES, TREADMILL_SERIES],
    });
    const outdoorOnly = paceType({
      points: [{ date: '2026-10-14', repPaceSecondsPerKm: 253, treadmill: false }],
    });
    const options = buildWorkoutPaceTrendOptions(outdoorOnly);
    expect(options.legend).toMatchObject({ data: [OUTDOOR_SERIES] });
    expect((options.series as ScatterSeriesOption[]).map((series) => series.name)).toEqual([
      'Pace line',
      OUTDOOR_SERIES,
    ]);
  });

  it('010 R7 - summary line shows thirty day change with tone', () => {
    expect(paceSummaryPrefix(paceType())).toBe(
      'Last 30 days vs previous 30 days: 4:20 /km → 4:13 /km · ',
    );
    expect(paceChangeText(paceType())).toBe('7 s/km faster');
    expect(paceChangeTone(paceType())).toBe('better');
    expect(paceChangeText(paceType({ changeSecondsPerKm: 3 }))).toBe('3 s/km slower');
    expect(paceChangeTone(paceType({ changeSecondsPerKm: 3 }))).toBe('worse');
    expect(paceChangeText(paceType({ changeSecondsPerKm: 0 }))).toBe('same pace');
    expect(paceChangeTone(paceType({ changeSecondsPerKm: 0 }))).toBe('neutral');
  });

  it('010 R7 - not enough data text per missing window', () => {
    const prefix = 'Not enough data for a 30-day comparison: no paced runs in';
    expect(notEnoughPaceText(paceType({ previous: null }))).toBe(`${prefix} the previous 30 days`);
    expect(notEnoughPaceText(paceType({ current: null }))).toBe(`${prefix} the last 30 days`);
    expect(notEnoughPaceText(paceType({ current: null, previous: null }))).toBe(
      `${prefix} either period`,
    );
    expect(paceSummaryPrefix(paceType({ previous: null }))).toBeNull();
  });

  it('010 R8 - runs without pace note', () => {
    expect(runsNotShownText(2)).toBe('2 runs without a rep pace are not shown');
    expect(runsNotShownText(1)).toBe('1 run without a rep pace is not shown');
    expect(runsNotShownText(0)).toBeNull();
  });
});
