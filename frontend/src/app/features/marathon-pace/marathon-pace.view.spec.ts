import { HttpErrorResponse } from '@angular/common/http';
import { marathonPace, mpSummary } from '../../testing/marathon-pace-fixtures';
import {
  isEmpty,
  pendingText,
  stateFromError,
  subtitleText,
  summaryItems,
} from './marathon-pace.view';

describe('marathon pace view helpers', () => {
  const httpError = (status: number, error: unknown) =>
    new HttpErrorResponse({ status, error, statusText: 'Error' });

  it('R7 - maps error responses to card states', () => {
    expect(stateFromError(httpError(401, { error: 'not_connected', message: 'x' }))).toBeNull();
    expect(
      stateFromError(httpError(404, { error: 'marathon_pace_not_configured', message: 'x' })),
    ).toEqual({ kind: 'notConfigured' });
    expect(
      stateFromError(httpError(422, { error: 'marathon_pace_invalid_config', message: 'Bad.' })),
    ).toEqual({ kind: 'invalidConfig', message: 'Bad.' });
    expect(stateFromError(httpError(404, { error: 'other', message: 'x' }))).toEqual({
      kind: 'error',
    });
    expect(stateFromError(httpError(500, null))).toEqual({ kind: 'error' });
    expect(stateFromError(new Error('network'))).toEqual({ kind: 'error' });
  });

  it('R6 - subtitle from the goal', () => {
    expect(subtitleText(marathonPace().goal)).toBe(
      'Goal 4:15 /km · laps from 4:05 to 4:25 /km, 200 m or longer, outdoor runs',
    );
  });

  it('R6 - summary values', () => {
    expect(summaryItems(mpSummary())).toEqual([
      { label: 'Total at MP', value: '41.0 km', caption: null },
      { label: 'This week at MP', value: '6.4 km', caption: null },
      { label: 'Share of this week', value: '25%', caption: 'of 26.0 km' },
    ]);
  });

  it('R6 - share shows dash with no runs this week', () => {
    const items = summaryItems(
      mpSummary({ thisWeekMpKm: 0, thisWeekTotalKm: 0, thisWeekSharePercent: null }),
    );
    expect(items[2]).toEqual({
      label: 'Share of this week',
      value: '–',
      caption: 'No runs this week',
    });
  });

  it('R9 - pending laps text', () => {
    expect(pendingText(0)).toBeNull();
    expect(pendingText(1)).toBe('1 run still importing laps; numbers will update.');
    expect(pendingText(2)).toBe('2 runs still importing laps; numbers will update.');
  });

  it('R7 - empty only when there are no MP km at all', () => {
    expect(isEmpty(marathonPace())).toBe(false);
    expect(isEmpty(marathonPace({ summary: mpSummary({ totalMpKm: 0, thisWeekMpKm: 0 }) }))).toBe(
      true,
    );
    expect(isEmpty(marathonPace({ summary: mpSummary({ totalMpKm: 0, thisWeekMpKm: 1.2 }) }))).toBe(
      false,
    );
  });
});
