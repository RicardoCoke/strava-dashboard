import { formatDate } from '@angular/common';
import { CoarseReason } from '../../core/api/workout-compare.models';
import { COMPARE_RUNS, PIECES, comparison } from '../../testing/workout-compare-fixtures';
import {
  coarseNote,
  hasNoPoints,
  listRowText,
  missingPointsNote,
  promptText,
  repHeaders,
  repRows,
  repsNote,
  showDriftFootnote,
  statsHeaders,
  statsRows,
  typeLabel,
} from './workout-compare.view';

describe('workout compare view', () => {
  it('R3 - row text, name and treadmill tag', () => {
    expect(listRowText(COMPARE_RUNS[0])).toBe('Tue 24 Nov · 8x3min · 10.1 km · 5:06 /km');
    expect(listRowText(COMPARE_RUNS[2])).toBe('Sun 8 Nov · Easy · 10.1 km · — /km');
    expect([typeLabel('easy'), typeLabel('long'), typeLabel('5x6min')]).toEqual([
      'Easy',
      'Long',
      '5x6min',
    ]);
  });

  it('R3 - prompt for zero and one selected runs', () => {
    expect(promptText(0)).toBe('Pick two runs to compare.');
    expect(promptText(1)).toBe('Pick one more run to compare.');
  });

  it('R7 - coarse note text per reason', () => {
    const retryAfter = '2026-11-26T08:15:00Z';
    const time = formatDate(retryAfter, 'HH:mm', 'en-US');
    const note = (reason: CoarseReason, runId: number | null = null, retry: string | null = null) =>
      coarseNote(
        comparison({ resolution: 'splits', coarse: { reason, runId, retryAfter: retry } }),
      );
    const prefix = 'Showing per-km splits, which are coarser than second-by-second data: ';

    expect(coarseNote(comparison())).toBeNull();
    expect(note('beforeGarmin', 1)).toBe(
      `${prefix}the Earlier · 10 Nov run was synced from Strava, which kept no detailed data.`,
    );
    expect(note('treadmill', 2)).toBe(`${prefix}the Later · 24 Nov run is a treadmill run.`);
    expect(note('noDetailFile', 2)).toBe(
      `${prefix}Garmin Connect has no detailed data for the Later · 24 Nov run.`,
    );
    expect(note('callLimit', null, retryAfter)).toBe(
      `${prefix}the Garmin Connect call limit is used up. Try again after ${time}.`,
    );
    expect(note('rateLimited', null, retryAfter)).toBe(
      `${prefix}Garmin Connect is limiting requests. Try again after ${time}.`,
    );
    expect(note('rateLimited')).toBe(
      `${prefix}Garmin Connect is limiting requests. Try again later.`,
    );
    expect(note('garminUnavailable')).toBe(`${prefix}couldn't reach Garmin Connect.`);
    expect(note('notConfigured')).toBe(`${prefix}Garmin Connect isn't set up for this person.`);
  });

  it('R7 - missing points notes', () => {
    const data = comparison();
    const oneEmpty = comparison({ runs: [{ ...data.runs[0], points: [] }, data.runs[1]] });
    const bothEmpty = comparison({
      runs: [
        { ...data.runs[0], points: [] },
        { ...data.runs[1], points: [] },
      ],
    });

    expect(missingPointsNote(data)).toBeNull();
    expect(missingPointsNote(oneEmpty)).toBe('No per-km data for the Earlier · 10 Nov run.');
    expect(hasNoPoints(oneEmpty)).toBe(false);
    expect(hasNoPoints(bothEmpty)).toBe(true);
  });

  it('R8 - stats table formats and drift footnote', () => {
    const data = comparison();

    expect(statsHeaders(data)).toEqual([
      { label: 'Earlier · 10 Nov', detail: '5x6min threshold' },
      { label: 'Later · 24 Nov', detail: '8x3min hills' },
    ]);
    expect(statsRows(data)).toEqual([
      { label: 'Distance', earlier: '11.2 km', later: '10.1 km' },
      { label: 'Moving time', earlier: '58:00', later: '51:30' },
      { label: 'Avg pace', earlier: '5:11 /km', later: '5:06 /km' },
      { label: 'Avg HR', earlier: '149 bpm', later: '147 bpm' },
      { label: 'Elevation gain', earlier: '38 m', later: '25 m' },
      { label: 'Efficiency factor', earlier: '1.30', later: '1.33' },
      { label: 'HR drift', earlier: '—', later: '+3.8%' },
    ]);
    expect(showDriftFootnote(data)).toBe(true);
  });

  it('R12 - rep table rows, all row and differences', () => {
    const data = comparison();

    expect(repHeaders(data)).toEqual([
      { label: 'Earlier · 10 Nov', detail: '5x6min as 3-min pieces' },
      { label: 'Later · 24 Nov', detail: '8x3min' },
    ]);
    expect(repRows(data)).toEqual([
      {
        number: 'All',
        earlier: '4:12 /km · 166 bpm',
        later: '4:04 /km · 163 bpm',
        difference: '8 s/km faster · −3.0 bpm',
      },
      {
        number: '1',
        earlier: '4:15 /km · 160 bpm',
        later: '4:06 /km · 158 bpm',
        difference: '9 s/km faster · −2.0 bpm',
      },
      {
        number: '2',
        earlier: '4:11 /km · 168 bpm',
        later: '4:03 /km',
        difference: '8 s/km faster',
      },
      { number: '3', earlier: '4:10 /km · 171 bpm', later: '—', difference: '' },
    ]);
    expect(
      repHeaders(
        comparison({ reps: { ...PIECES, mode: 'reps', pieceMinutes: null, piecesRunId: null } }),
      )[0].detail,
    ).toBe('5x6min');
  });

  it('R12 - unavailable note with whole-session averages', () => {
    const unavailable = { ...PIECES, mode: 'unavailable' as const, average: null, pairs: [] };
    const noData = comparison({ reps: { ...unavailable, unavailableReason: 'noDetailedData' } });
    const notFound = comparison({
      reps: { ...unavailable, unavailableReason: 'repsNotFound', unavailableRunId: 2 },
    });

    expect(repsNote(noData)).toEqual({
      message: "Reps can't be compared without detailed data for both runs.",
      averages: ['Earlier · 10 Nov: 5:11 /km · 149 bpm', 'Later · 24 Nov: 5:06 /km · 147 bpm'],
    });
    expect(repsNote(notFound)?.message).toBe(
      "Couldn't find the 8x3min reps in the Later · 24 Nov run.",
    );
    expect(repRows(noData)).toEqual([]);
    expect(repsNote(comparison())).toBeNull();
  });
});
