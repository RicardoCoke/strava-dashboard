import { HttpErrorResponse } from '@angular/common/http';
import { DestroyRef, Injectable, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { Observable, Subscription, timer } from 'rxjs';
import { SyncStatusResponse, isSyncStatusResponse } from '../api/sync.models';
import { SyncService } from '../api/sync.service';

export const POLL_INTERVAL_MS = 5000;
export const ERROR_RETRY_MS = 60000;
export const MIN_DELAY_MS = 1000;

/** What the sync banner shows: nothing yet, a failed status check, or the last status. */
export type SyncBannerState = 'none' | 'failed' | SyncStatusResponse;

/**
 * Drives the background sync while the dashboard page is open (feature 009): checks the status, polls while a sync
 * runs, starts the next one when the backend says it is due, and bumps [dataVersion] when a sync finishes so the
 * cards can refresh. Provided by the page, so every timer stops when the page is left.
 */
@Injectable()
export class SyncStore {
  private readonly api = inject(SyncService);
  private readonly destroyRef = inject(DestroyRef);

  private readonly statusSignal = signal<SyncStatusResponse | null>(null);
  private readonly failedSignal = signal(false);
  private readonly versionSignal = signal(0);
  private next: Subscription | null = null;
  private started = false;

  readonly status = this.statusSignal.asReadonly();
  readonly failed = this.failedSignal.asReadonly();
  /** Starts at 0 and goes up by one each time a sync finishes. */
  readonly dataVersion = this.versionSignal.asReadonly();
  readonly bannerState = computed<SyncBannerState>(() =>
    this.failedSignal() ? 'failed' : (this.statusSignal() ?? 'none'),
  );

  constructor() {
    this.destroyRef.onDestroy(() => this.next?.unsubscribe());
  }

  /** Checks the status once; everything after that is scheduled from the answers. */
  start(): void {
    if (!this.started) {
      this.started = true;
      this.send(this.api.getStatus());
    }
  }

  private send(request: Observable<unknown>): void {
    this.next?.unsubscribe();
    this.next = request.pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: (body) => (isSyncStatusResponse(body) ? this.receive(body) : this.fail()),
      error: (error: unknown) => {
        // A 401 goes to the interceptor, which navigates to /connect; nothing is left to sync.
        if (!(error instanceof HttpErrorResponse && error.status === 401)) {
          this.fail();
        }
      },
    });
  }

  private receive(status: SyncStatusResponse): void {
    const wasSyncing = this.statusSignal()?.status === 'syncing';
    this.failedSignal.set(false);
    this.statusSignal.set(status);
    if (status.status === 'syncing') {
      this.later(POLL_INTERVAL_MS, () => this.api.getStatus());
      return;
    }
    if (wasSyncing) {
      this.versionSignal.update((version) => version + 1);
    }
    const dueInMs = (status.nextSyncInSeconds ?? 0) * 1000;
    this.later(Math.max(dueInMs, MIN_DELAY_MS), () => this.api.start());
  }

  private fail(): void {
    this.failedSignal.set(true);
    this.later(ERROR_RETRY_MS, () => this.api.getStatus());
  }

  private later(delayMs: number, request: () => Observable<unknown>): void {
    this.next?.unsubscribe();
    this.next = timer(delayMs)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe(() => this.send(request()));
  }
}
