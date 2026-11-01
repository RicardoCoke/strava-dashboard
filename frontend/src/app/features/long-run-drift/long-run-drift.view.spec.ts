import { HttpErrorResponse } from '@angular/common/http';
import { emptyLongRunDrift, longRunDrift } from '../../testing/long-run-drift-fixtures';
import {
  driftStateFromError,
  emptyKind,
  excludedText,
  formatSignedPct,
  headerTexts,
  legendText,
  pendingText,
} from './long-run-drift.view';

describe('long-run drift view', () => {
  it('R6 - header shows latest and average with zone class', () => {
    expect(headerTexts(longRunDrift())).toEqual([
      { text: 'Latest: +3.8% · 25 Oct', zone: 'good' },
      { text: 'Avg last 4: +4.6%', zone: 'good' },
    ]);
    const negative = longRunDrift({
      latest: { date: '2026-10-25', driftPct: -1.2, zone: 'good' },
      lastFourAverage: { driftPct: 5, zone: 'watch', runCount: 2 },
    });
    expect(headerTexts(negative).map((item) => item.text)).toEqual([
      'Latest: −1.2% · 25 Oct',
      'Avg last 2: +5.0%',
    ]);
  });

  it('R6 - signed percent uses a real minus and no sign for zero', () => {
    expect(formatSignedPct(3.8)).toBe('+3.8');
    expect(formatSignedPct(-1.2)).toBe('−1.2');
    expect(formatSignedPct(0)).toBe('0.0');
    expect(formatSignedPct(-0.04)).toBe('0.0');
  });

  it('R6 - excluded text lists non zero reasons', () => {
    expect(excludedText({ total: 3, treadmill: 1, tooShort: 0, missingData: 2 })).toBe(
      '3 long runs left out: 1 treadmill, 2 missing HR or speed data.',
    );
    expect(excludedText({ total: 1, treadmill: 0, tooShort: 1, missingData: 0 })).toBe(
      '1 long run left out: 1 under 60 min usable.',
    );
    expect(excludedText({ total: 0, treadmill: 0, tooShort: 0, missingData: 0 })).toBeNull();
  });

  it('R6 - pending text singular and plural', () => {
    expect(pendingText(1)).toBe('1 long run still syncing');
    expect(pendingText(3)).toBe('3 long runs still syncing');
    expect(pendingText(0)).toBeNull();
  });

  it('R6 - legend from thresholds', () => {
    expect(legendText({ goodBelowPct: 5, poorAbovePct: 10 })).toBe(
      'Good < 5% · Watch 5–10% · Poor > 10%',
    );
  });

  it('R6 - empty kinds', () => {
    expect(emptyKind(emptyLongRunDrift())).toBe('none');
    expect(emptyKind({ ...emptyLongRunDrift(), pendingCount: 2 })).toBe('noDrift');
    expect(
      emptyKind({
        ...emptyLongRunDrift(),
        excluded: { total: 1, treadmill: 1, tooShort: 0, missingData: 0 },
      }),
    ).toBe('noDrift');
    expect(emptyKind(longRunDrift())).toBeNull();
  });

  it('R6 - 401 is left to the interceptor', () => {
    expect(driftStateFromError(new HttpErrorResponse({ status: 401 }))).toBeNull();
    expect(driftStateFromError(new HttpErrorResponse({ status: 500 }))).toEqual({ kind: 'error' });
  });
});
