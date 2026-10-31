import { provideHttpClient, withXsrfConfiguration } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { syncStatus } from '../../testing/sync-store-stub';
import { isSyncStatusResponse } from './sync.models';
import { SyncService } from './sync.service';

describe('SyncService', () => {
  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(withXsrfConfiguration({})), provideHttpClientTesting()],
    });
  });

  it('009 R2 - getStatus calls GET /api/sync', () => {
    const http = TestBed.inject(HttpTestingController);
    TestBed.inject(SyncService).getStatus().subscribe();
    expect(http.expectOne('/api/sync').request.method).toBe('GET');
    http.verify();
  });

  it('R6 - start posts to api sync', () => {
    const http = TestBed.inject(HttpTestingController);
    TestBed.inject(SyncService).start().subscribe();
    const request = http.expectOne('/api/sync');
    expect(request.request.method).toBe('POST');
    expect(request.request.body).toEqual({});
    http.verify();
  });

  it('009 R2 - shape guard accepts the contract and rejects other bodies', () => {
    expect(isSyncStatusResponse(syncStatus())).toBe(true);
    expect(isSyncStatusResponse(syncStatus({ status: 'syncing', nextSyncInSeconds: null }))).toBe(
      true,
    );
    expect(isSyncStatusResponse({ ...syncStatus(), status: 'done' })).toBe(false);
    expect(isSyncStatusResponse({ status: 'ok' })).toBe(false);
    expect(isSyncStatusResponse(null)).toBe(false);
  });
});
