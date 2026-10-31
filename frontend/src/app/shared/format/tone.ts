export type Tone = 'better' | 'worse' | 'neutral';

/** For bpm and s/km alike lower is better: negative → better, positive → worse, 0 or −0 → neutral. */
export function toneForChange(delta: number): Tone {
  if (delta < 0) {
    return 'better';
  }
  return delta > 0 ? 'worse' : 'neutral';
}
