import { formatDate } from '@angular/common';
import { TestBed } from '@angular/core/testing';
import { SyncInfo } from '../../../core/api/running-dashboard.models';
import { SyncBanner, syncBannerText } from './sync-banner';

describe('SyncBanner', () => {
  const base: SyncInfo = {
    status: 'ok',
    lastSyncedAt: null,
    retryAfter: null,
    runsPendingDetail: 0,
  };

  it('R5 - sync banner text per status', () => {
    const retryAfter = '2026-10-29T10:15:00Z';
    expect(syncBannerText({ ...base, status: 'rateLimited', retryAfter })).toBe(
      `Strava rate limit reached. Showing saved data; it will refresh after ${formatDate(retryAfter, 'HH:mm', 'en-US')}.`,
    );
    const saved = '2026-10-29T08:12:03Z';
    expect(syncBannerText({ ...base, status: 'stravaUnavailable', lastSyncedAt: saved })).toBe(
      `Couldn't reach Strava. Showing data saved at ${formatDate(saved, 'd MMM y, HH:mm', 'en-US')}.`,
    );
    expect(syncBannerText({ ...base, status: 'stravaUnavailable' })).toBe(
      "Couldn't reach Strava. No data saved yet.",
    );
    expect(syncBannerText({ ...base, status: 'partial', runsPendingDetail: 5 })).toBe(
      'Still importing runs from Strava (5 remaining). Reload in a while to see the rest.',
    );
    expect(syncBannerText(base)).toBeNull();
  });

  it('R5 - no banner when status is ok', () => {
    const fixture = TestBed.createComponent(SyncBanner);
    fixture.componentRef.setInput('sync', base);
    fixture.detectChanges();
    expect((fixture.nativeElement as HTMLElement).querySelector('.banner')).toBeNull();
  });
});
