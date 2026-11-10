import { HttpErrorResponse } from '@angular/common/http';
import { WellnessSourceStatus } from '../../core/api/wellness.models';
import { wellness } from '../../testing/wellness-fixtures';
import { emptyText, latestTiles, staleText, wellnessStateFromError } from './wellness.view';

describe('wellness view', () => {
  const source = (status: WellnessSourceStatus) => ({ status, lastFetchedAt: null });

  it('R9 - empty text per source status', () => {
    expect(emptyText(source('pending'))).toBe('Waiting for the first sync from Garmin Connect.');
    expect(emptyText(source('ok'))).toBe('No resting HR or HRV in the last 12 weeks.');
    expect(emptyText(source('keyRejected'))).toBe(
      'Garmin Connect rejected the API key. Check GARMIN_API_KEY, then restart the backend.',
    );
    const unreachable = "Couldn't reach Garmin Connect. It will retry at the next sync.";
    expect(emptyText(source('rateLimited'))).toBe(unreachable);
    expect(emptyText(source('unavailable'))).toBe(unreachable);
  });

  it('R9 - stale line only when the last fetch failed', () => {
    expect(staleText(source('ok'))).toBeNull();
    expect(staleText(source('pending'))).toBeNull();
    expect(staleText(source('keyRejected'))).toBe(
      'Showing saved data: Garmin Connect rejected the API key.',
    );
    expect(staleText(source('unavailable'))).toBe(
      "Showing saved data: Garmin Connect couldn't be reached at the last sync.",
    );
    expect(staleText(source('rateLimited'))).toBe(staleText(source('unavailable')));
  });

  it('R9 - 404 not configured, anything else error', () => {
    const notConfigured = new HttpErrorResponse({
      status: 404,
      error: { error: 'wellness_not_configured', message: 'Garmin Connect is not connected.' },
    });
    expect(wellnessStateFromError(notConfigured)).toEqual({ kind: 'notConfigured' });
    expect(wellnessStateFromError(new HttpErrorResponse({ status: 401 }))).toEqual({
      kind: 'error',
    });
    expect(wellnessStateFromError(new HttpErrorResponse({ status: 404 }))).toEqual({
      kind: 'error',
    });
    expect(wellnessStateFromError(new HttpErrorResponse({ status: 500 }))).toEqual({
      kind: 'error',
    });
    expect(wellnessStateFromError(new Error('x'))).toEqual({ kind: 'error' });
  });

  it('R6 - tiles show value, delta, date and flag text', () => {
    expect(latestTiles(wellness())).toEqual([
      {
        label: 'Resting HR',
        value: '53 bpm',
        delta: '+5.6 bpm vs baseline',
        caption: 'on Sat 31 Oct',
        flag: 'Above baseline',
      },
      {
        label: 'HRV',
        value: '58 ms',
        delta: '−4% vs baseline',
        caption: 'on Fri 30 Oct',
        flag: null,
      },
    ]);
    const none = latestTiles(wellness({ latest: { restingHr: null, hrv: null } }));
    expect(none.map((tile) => tile.value)).toEqual([null, null]);
  });
});
