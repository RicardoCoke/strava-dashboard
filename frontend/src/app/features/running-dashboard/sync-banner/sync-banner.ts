import { formatDate } from '@angular/common';
import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';
import { SyncInfo } from '../../../core/api/running-dashboard.models';

const LOCALE = 'en-US';

export function syncBannerText(sync: SyncInfo): string | null {
  switch (sync.status) {
    case 'rateLimited': {
      const time = sync.retryAfter ? formatDate(sync.retryAfter, 'HH:mm', LOCALE) : 'a while';
      return `Strava rate limit reached. Showing saved data; it will refresh after ${time}.`;
    }
    case 'stravaUnavailable':
      return sync.lastSyncedAt
        ? `Couldn't reach Strava. Showing data saved at ${formatDate(sync.lastSyncedAt, 'd MMM y, HH:mm', LOCALE)}.`
        : "Couldn't reach Strava. No data saved yet.";
    case 'partial':
      return `Still importing runs from Strava (${sync.runsPendingDetail} remaining). Reload in a while to see the rest.`;
    case 'ok':
      return null;
  }
}

@Component({
  selector: 'app-sync-banner',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @if (text(); as message) {
      <p class="banner notice" role="status">{{ message }}</p>
    }
  `,
})
export class SyncBanner {
  readonly sync = input.required<SyncInfo>();
  protected readonly text = computed(() => syncBannerText(this.sync()));
}
