import { HttpErrorResponse } from '@angular/common/http';
import { normalizedVo2max } from '../../testing/vo2max-fixtures';
import { normalizedFootnoteText, normalizedStateFromError } from './vo2max.view';

describe('vo2max view (016)', () => {
  it('R12 - normalized error states', () => {
    expect(normalizedStateFromError(new HttpErrorResponse({ status: 500 }))).toEqual({
      kind: 'error',
    });
    expect(
      normalizedStateFromError(
        new HttpErrorResponse({
          status: 422,
          error: { error: 'vo2max_invalid_config', message: 'M' },
        }),
      ),
    ).toEqual({ kind: 'invalidConfig', message: 'M' });
    expect(normalizedStateFromError(new Error('network'))).toEqual({ kind: 'error' });
  });

  it('R10 - footnote with split import pending', () => {
    expect(normalizedFootnoteText(normalizedVo2max({ runsPendingDetail: 12 }))).toEqual([
      'Max HR 194 bpm (estimate; set MAX_HR_BPM to change)',
      '12 runs still importing split data; numbers will update.',
    ]);
    expect(normalizedFootnoteText(normalizedVo2max())).toEqual([
      'Max HR 194 bpm (estimate; set MAX_HR_BPM to change)',
    ]);
  });
});
