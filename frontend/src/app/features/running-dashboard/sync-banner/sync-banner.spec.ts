import { formatDate } from '@angular/common';
import { TestBed } from '@angular/core/testing';
import { syncStatus } from '../../../testing/sync-store-stub';
import { SyncBanner, syncBannerText } from './sync-banner';

describe('SyncBanner', () => {
  it('009 R8 - banner text per status', () => {
    const retryAfter = '2026-10-29T10:15:00Z';
    const saved = '2026-10-29T08:12:03Z';
    expect(syncBannerText('none')).toBeNull();
    expect(syncBannerText('failed')).toBe("Couldn't check the Strava sync. Showing saved data.");
    expect(syncBannerText(syncStatus({ status: 'syncing', nextSyncInSeconds: null }))).toBe(
      'Syncing with Strava…',
    );
    expect(syncBannerText(syncStatus({ status: 'partial', runsPendingDetail: 5 }))).toBe(
      'Still importing runs from Strava (5 remaining). This continues automatically.',
    );
    expect(syncBannerText(syncStatus({ status: 'rateLimited', retryAfter }))).toBe(
      `Strava rate limit reached. Showing saved data; syncing resumes after ${formatDate(retryAfter, 'HH:mm', 'en-US')}.`,
    );
    expect(syncBannerText(syncStatus({ status: 'rateLimited' }))).toBe(
      'Strava rate limit reached. Showing saved data; syncing resumes after a while.',
    );
    expect(syncBannerText(syncStatus({ status: 'stravaUnavailable', lastSyncedAt: saved }))).toBe(
      `Couldn't sync with Strava. Showing data saved at ${formatDate(saved, 'd MMM y, HH:mm', 'en-US')}.`,
    );
    expect(syncBannerText(syncStatus({ status: 'stravaUnavailable', lastSyncedAt: null }))).toBe(
      "Couldn't sync with Strava. No data saved yet.",
    );
    expect(syncBannerText(syncStatus())).toBeNull();
  });

  it('009 R8 - shows syncing with strava while syncing', () => {
    const fixture = TestBed.createComponent(SyncBanner);
    fixture.componentRef.setInput(
      'state',
      syncStatus({ status: 'syncing', nextSyncInSeconds: null }),
    );
    fixture.detectChanges();
    const banner = (fixture.nativeElement as HTMLElement).querySelector('.banner');
    expect(banner?.textContent?.trim()).toBe('Syncing with Strava…');
    expect(banner?.getAttribute('role')).toBe('status');
  });

  it('R5 - no banner when status is ok', () => {
    const fixture = TestBed.createComponent(SyncBanner);
    fixture.componentRef.setInput('state', syncStatus());
    fixture.detectChanges();
    expect((fixture.nativeElement as HTMLElement).querySelector('.banner')).toBeNull();
  });
});
