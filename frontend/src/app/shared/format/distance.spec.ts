import { formatKmDifference } from './distance';

describe('formatKmDifference', () => {
  it('007 R3 - ahead shows plus km', () => {
    expect(formatKmDifference(3.4)).toBe('+3.4 km');
    expect(formatKmDifference(10)).toBe('+10.0 km');
  });

  it('007 R3 - behind shows minus km', () => {
    expect(formatKmDifference(-4.2)).toBe('−4.2 km');
  });

  it('007 R3 - equal shows plus-minus zero', () => {
    expect(formatKmDifference(0)).toBe('±0.0 km');
    expect(formatKmDifference(-0)).toBe('±0.0 km');
  });
});
