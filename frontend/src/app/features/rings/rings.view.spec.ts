import { RingsResponse } from '../../core/api/rings.models';
import { LOADING_RINGS, ringViews } from './rings.view';

// All data here is invented.
const full: RingsResponse = {
  date: '2026-11-09',
  sleep: { percent: 82, fill: 0.82, deltaLabel: '+5 pts vs 30-day avg' },
  recovery: { percent: 75, zone: 'green', fill: 0.75, deltaLabel: null },
  strain: { value: 12, fill: 0.571, deltaLabel: '−0.8 vs 30-day avg' },
};

describe('ringViews', () => {
  it('R8 - ring values, order and lines', () => {
    const [sleep, recovery, strain] = ringViews(full);
    expect([sleep.label, recovery.label, strain.label]).toEqual(['Sleep', 'Recovery', 'Strain']);
    expect(sleep.valueText).toBe('82%');
    expect(recovery.valueText).toBe('75%');
    expect(strain.valueText).toBe('12.0');
    expect(sleep.line).toBe('+5 pts vs 30-day avg');
    expect(recovery.line).toBe('No 30-day average yet');
  });

  it('R9 - ring colour per kind and zone', () => {
    expect(ringViews(full).map((ring) => ring.colour)).toEqual(['sleep', 'green', 'strain']);
    const red = ringViews({ ...full, recovery: { ...full.recovery!, zone: 'red' } });
    expect(red[1].colour).toBe('red');
  });

  it('R8 - aria labels per ring', () => {
    expect(ringViews(full).map((ring) => ring.ariaLabel)).toEqual([
      'Sleep 82%, +5 pts vs 30-day avg',
      'Recovery 75%, green, No 30-day average yet',
      'Strain 12.0 of 21, −0.8 vs 30-day avg',
    ]);
  });

  it('R8 - empty ring shows dash and no data', () => {
    const rings = ringViews({ date: '2026-11-09', sleep: null, recovery: null, strain: null });
    expect(rings.every((ring) => ring.valueText === null && ring.fill === 0)).toBe(true);
    expect(rings.map((ring) => ring.line)).toEqual(['No data', 'No data', 'No data']);
    expect(rings[0].ariaLabel).toBe('Sleep, no data');
    expect(LOADING_RINGS.every((ring) => ring.line === '')).toBe(true);
  });
});
