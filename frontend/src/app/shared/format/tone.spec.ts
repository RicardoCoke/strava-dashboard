import { formatBpmChange } from './heart-rate';
import { toneForChange } from './tone';

describe('tone and bpm formatting', () => {
  it('002 R8 - tone for negative positive and zero change', () => {
    expect(toneForChange(-7)).toBe('better');
    expect(toneForChange(3)).toBe('worse');
    expect(toneForChange(0)).toBe('neutral');
    expect(toneForChange(-0)).toBe('neutral');
  });

  it('002 R6 - bpm change format', () => {
    expect(formatBpmChange(-8)).toBe('−8.0');
    expect(formatBpmChange(3.2)).toBe('+3.2');
    expect(formatBpmChange(-0)).toBe('0.0');
  });
});
