import { computed, signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { SyncStatusResponse } from '../core/api/sync.models';
import { SyncBannerState, SyncStore } from '../core/sync/sync.store';
import { TrainingPlanStore } from '../features/calendar-like-build/training-plan.store';
import { RunningDashboardPage } from '../features/running-dashboard/running-dashboard-page';

export function syncStatus(overrides: Partial<SyncStatusResponse> = {}): SyncStatusResponse {
  return {
    status: 'ok',
    lastSyncedAt: '2026-10-29T08:12:03Z',
    retryAfter: null,
    runsPendingDetail: 0,
    nextSyncInSeconds: 900,
    ...overrides,
  };
}

/** A sync store a test drives by hand: no requests, no timers. */
export class SyncStoreStub {
  readonly status = signal<SyncStatusResponse | null>(null);
  readonly failed = signal(false);
  readonly dataVersion = signal(0);
  readonly bannerState = computed<SyncBannerState>(() =>
    this.failed() ? 'failed' : (this.status() ?? 'none'),
  );
  started = false;

  start(): void {
    this.started = true;
  }

  /** A sync started and then finished with [status], as the real store reports it. */
  finish(status: SyncStatusResponse = syncStatus()): void {
    this.status.set(status);
    this.dataVersion.update((version) => version + 1);
  }
}

/** Gives the dashboard page (and the sections in it) a hand-driven sync store instead of the real one. */
export function stubSyncStore(): SyncStoreStub {
  const stub = new SyncStoreStub();
  TestBed.overrideComponent(RunningDashboardPage, {
    set: { providers: [TrainingPlanStore, { provide: SyncStore, useValue: stub }] },
  });
  return stub;
}
