// Mirrors the JSON of GET and POST /api/sync
// (backend api/SyncStatusResponse.kt, docs/features/009-background-sync/specs.md).

export type SyncStatus = 'syncing' | 'ok' | 'partial' | 'rateLimited' | 'stravaUnavailable';

export interface SyncStatusResponse {
  status: SyncStatus;
  /** ISO-8601 UTC instant. */
  lastSyncedAt: string | null;
  /** ISO-8601 UTC instant. */
  retryAfter: string | null;
  runsPendingDetail: number;
  /** Seconds until a sync may start; null while syncing. */
  nextSyncInSeconds: number | null;
}

const STATUSES: readonly string[] = [
  'syncing',
  'ok',
  'partial',
  'rateLimited',
  'stravaUnavailable',
];

function isObject(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null;
}

/** A light shape check, so an unexpected body becomes an error state rather than a broken banner. */
export function isSyncStatusResponse(value: unknown): value is SyncStatusResponse {
  if (!isObject(value)) {
    return false;
  }
  const next = value['nextSyncInSeconds'];
  return (
    typeof value['status'] === 'string' &&
    STATUSES.includes(value['status']) &&
    typeof value['runsPendingDetail'] === 'number' &&
    (next === null || typeof next === 'number')
  );
}
