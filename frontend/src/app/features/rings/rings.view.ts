import { RecoveryZone, RingsResponse } from '../../core/api/rings.models';

export type RingColour = 'sleep' | 'strain' | RecoveryZone;

/** One ring as drawn: [valueText] null means an empty ring (track only). */
export interface RingView {
  label: string;
  valueText: string | null;
  fill: number;
  colour: RingColour;
  line: string;
  ariaLabel: string;
}

const NO_AVERAGE = 'No 30-day average yet';

function emptyRing(label: string, colour: RingColour): RingView {
  return {
    label,
    valueText: null,
    fill: 0,
    colour,
    line: 'No data',
    ariaLabel: `${label}, no data`,
  };
}

/** Sleep, Recovery, Strain, in Whoop's order (020 R8). */
export function ringViews(data: RingsResponse): RingView[] {
  const { sleep, recovery, strain } = data;
  return [
    sleep
      ? {
          label: 'Sleep',
          valueText: `${sleep.percent}%`,
          fill: sleep.fill,
          colour: 'sleep',
          line: sleep.deltaLabel ?? NO_AVERAGE,
          ariaLabel: `Sleep ${sleep.percent}%, ${sleep.deltaLabel ?? NO_AVERAGE}`,
        }
      : emptyRing('Sleep', 'sleep'),
    recovery
      ? {
          label: 'Recovery',
          valueText: `${recovery.percent}%`,
          fill: recovery.fill,
          colour: recovery.zone,
          line: recovery.deltaLabel ?? NO_AVERAGE,
          ariaLabel: `Recovery ${recovery.percent}%, ${recovery.zone}, ${recovery.deltaLabel ?? NO_AVERAGE}`,
        }
      : emptyRing('Recovery', 'green'),
    strain
      ? {
          label: 'Strain',
          valueText: strain.value.toFixed(1),
          fill: strain.fill,
          colour: 'strain',
          line: strain.deltaLabel ?? NO_AVERAGE,
          ariaLabel: `Strain ${strain.value.toFixed(1)} of 21, ${strain.deltaLabel ?? NO_AVERAGE}`,
        }
      : emptyRing('Strain', 'strain'),
  ];
}

/** Track-only rings while loading. */
export const LOADING_RINGS: RingView[] = ringViews({
  date: '',
  sleep: null,
  recovery: null,
  strain: null,
}).map((ring) => ({ ...ring, line: '' }));
