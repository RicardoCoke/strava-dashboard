import { normalizedVo2max } from '../../testing/vo2max-fixtures';
import { isNormalizedVo2maxResponse } from './normalized-vo2max.models';

describe('isNormalizedVo2maxResponse', () => {
  it('R7 - accepts a normalized response', () => {
    expect(isNormalizedVo2maxResponse(normalizedVo2max())).toBe(true);
  });

  it('R7 - isNormalizedVo2maxResponse rejects missing originalVo2max', () => {
    const data = normalizedVo2max();
    const weeks = data.weeks.map((week) => {
      const copy: Partial<typeof week> = { ...week };
      delete copy.originalVo2max;
      return copy;
    });
    expect(isNormalizedVo2maxResponse({ ...data, weeks })).toBe(false);
    expect(
      isNormalizedVo2maxResponse({
        ...data,
        weeks: data.weeks.map((week) => ({ ...week, originalVo2max: 'x' })),
      }),
    ).toBe(false);
    expect(isNormalizedVo2maxResponse(null)).toBe(false);
  });
});
