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

// GET /api/rings/history (docs/features/023-recovery-tab/specs.md).

export type HistoryRange = 7 | 30 | 90;

export interface RingsHistoryDay {
  date: string;
  sleepPercent: number | null;
  recoveryPercent: number | null;
  recoveryZone: RecoveryZone | null;
  strain: number | null;
}

export interface RingsHistoryResponse {
  days: RingsHistoryDay[];
}

/** A light shape check, so an unexpected 200 body shows the error state instead of broken graphs. */
export function isRingsHistoryResponse(value: unknown): value is RingsHistoryResponse {
  if (typeof value !== 'object' || value === null) {
    return false;
  }
  const days = (value as Record<string, unknown>)['days'];
  return (
    Array.isArray(days) &&
    days.every(
      (day: unknown) =>
        typeof day === 'object' &&
        day !== null &&
        typeof (day as Record<string, unknown>)['date'] === 'string',
    )
  );
}
