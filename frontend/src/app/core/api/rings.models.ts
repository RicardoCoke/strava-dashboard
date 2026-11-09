// The JSON contract of GET /api/rings (docs/features/020-whoop-rings/specs.md).

export type RecoveryZone = 'green' | 'yellow' | 'red';

export interface SleepRing {
  percent: number;
  fill: number;
  deltaLabel: string | null;
}

export interface RecoveryRing {
  percent: number;
  zone: RecoveryZone;
  fill: number;
  deltaLabel: string | null;
}

export interface StrainRing {
  value: number;
  fill: number;
  deltaLabel: string | null;
}

export interface RingsResponse {
  date: string;
  sleep: SleepRing | null;
  recovery: RecoveryRing | null;
  strain: StrainRing | null;
}

function isRing(value: unknown): boolean {
  return (
    value === null ||
    (typeof value === 'object' && typeof (value as Record<string, unknown>)['fill'] === 'number')
  );
}

/** A light shape check, so an unexpected 200 body shows the error state instead of breaking the rings. */
export function isRingsResponse(value: unknown): value is RingsResponse {
  if (typeof value !== 'object' || value === null) {
    return false;
  }
  const body = value as Record<string, unknown>;
  return (
    typeof body['date'] === 'string' &&
    isRing(body['sleep']) &&
    isRing(body['recovery']) &&
    isRing(body['strain'])
  );
}
