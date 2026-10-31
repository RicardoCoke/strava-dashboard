import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { Injector } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { Router } from '@angular/router';
import { syncStatus } from '../../testing/sync-store-stub';
import { SyncStatusResponse } from '../api/sync.models';
import { apiErrorInterceptor } from '../interceptors/api-error.interceptor';
import { ERROR_RETRY_MS, MIN_DELAY_MS, POLL_INTERVAL_MS, SyncStore } from './sync.store';

describe('SyncStore', () => {
  let http: HttpTestingController;
  let store: SyncStore;
  let injector: Injector;

  const syncing = (): SyncStatusResponse =>
    syncStatus({ status: 'syncing', nextSyncInSeconds: null });

  beforeEach(() => {
    vi.useFakeTimers();
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(withInterceptors([apiErrorInterceptor])),
        provideHttpClientTesting(),
        { provide: Router, useValue: { navigate: vi.fn(), navigateByUrl: vi.fn() } },
      ],
    });
    http = TestBed.inject(HttpTestingController);
    // A child injector stands in for the page: destroying it is leaving the page.
    injector = Injector.create({ providers: [SyncStore], parent: TestBed.inject(Injector) });
    store = injector.get(SyncStore);
    store.start();
  });

  afterEach(() => {
    vi.useRealTimers();
  });

  function answer(method: 'GET' | 'POST', body: SyncStatusResponse): void {
    const request = http.expectOne('/api/sync');
    expect(request.request.method).toBe(method);
    request.flush(body);
  }

  it('R7 - posts after next sync seconds', () => {
    answer('GET', syncStatus({ nextSyncInSeconds: 30 }));

    vi.advanceTimersByTime(29_999);
    http.expectNone('/api/sync');
    vi.advanceTimersByTime(1);
    answer('POST', syncing());
  });

  it('R7 - posts after one second when a sync is due now', () => {
    answer('GET', syncStatus({ nextSyncInSeconds: 0 }));
    vi.advanceTimersByTime(MIN_DELAY_MS - 1);
    http.expectNone('/api/sync');
    vi.advanceTimersByTime(1);
    answer('POST', syncing());
  });

  it('R7 - polls every 5 seconds while syncing', () => {
    answer('GET', syncing());
    vi.advanceTimersByTime(POLL_INTERVAL_MS);
    answer('GET', syncing());
    vi.advanceTimersByTime(POLL_INTERVAL_MS);
    answer('GET', syncStatus({ nextSyncInSeconds: 900 }));

    vi.advanceTimersByTime(POLL_INTERVAL_MS);
    http.expectNone('/api/sync');
    expect(store.status()?.status).toBe('ok');
  });

  it('R7 - no post before next sync seconds after reload', () => {
    answer('GET', syncStatus({ nextSyncInSeconds: 600 }));
    vi.advanceTimersByTime(599_000);
    http.expectNone('/api/sync');
  });

  it('R7 - posts again after each 15 minute interval', () => {
    answer('GET', syncStatus({ nextSyncInSeconds: 900 }));
    for (let round = 0; round < 2; round++) {
      vi.advanceTimersByTime(900_000);
      answer('POST', syncing());
      vi.advanceTimersByTime(POLL_INTERVAL_MS);
      answer('GET', syncStatus({ nextSyncInSeconds: 900 }));
    }
  });

  it('R7 - status error sets failed and retries after 60 seconds', () => {
    http.expectOne('/api/sync').flush(null, { status: 500, statusText: 'Error' });
    expect(store.failed()).toBe(true);
    expect(store.bannerState()).toBe('failed');

    vi.advanceTimersByTime(ERROR_RETRY_MS - 1);
    http.expectNone('/api/sync');
    vi.advanceTimersByTime(1);
    answer('GET', syncStatus());
    expect(store.failed()).toBe(false);
  });

  it('R7 - a 401 stops the store', () => {
    http
      .expectOne('/api/sync')
      .flush({ error: 'not_connected', message: 'x' }, { status: 401, statusText: 'Unauthorized' });
    vi.advanceTimersByTime(ERROR_RETRY_MS * 2);
    http.expectNone('/api/sync');
    expect(store.failed()).toBe(false);
  });

  it('R7 - destroying the page stops all sync requests', () => {
    answer('GET', syncing());
    (injector as unknown as { destroy(): void }).destroy();
    vi.advanceTimersByTime(ERROR_RETRY_MS * 20);
    http.expectNone('/api/sync');
  });

  it('R9 - data version increments once when syncing ends', () => {
    answer('GET', syncStatus({ nextSyncInSeconds: 0 }));
    expect(store.dataVersion()).toBe(0);
    vi.advanceTimersByTime(MIN_DELAY_MS);
    answer('POST', syncing());
    vi.advanceTimersByTime(POLL_INTERVAL_MS);
    answer('GET', syncing());
    expect(store.dataVersion()).toBe(0);
    vi.advanceTimersByTime(POLL_INTERVAL_MS);
    answer('GET', syncStatus({ status: 'partial', runsPendingDetail: 40, nextSyncInSeconds: 0 }));
    expect(store.dataVersion()).toBe(1);
    expect(store.bannerState()).toEqual(
      syncStatus({ status: 'partial', runsPendingDetail: 40, nextSyncInSeconds: 0 }),
    );
  });
});
