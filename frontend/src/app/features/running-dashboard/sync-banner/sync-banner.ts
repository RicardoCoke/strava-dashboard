import { formatDate } from '@angular/common';
import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';
import { SyncBannerState } from '../../../core/sync/sync.store';

const LOCALE = 'en-US';

/** The banner text for a sync state (feature 009, R8), or null for no banner. */
export function syncBannerText(state: SyncBannerState): string | null {
  if (state === 'none') {
    return null;
  }
  if (state === 'failed') {
    return "Couldn't check the Strava sync. Showing saved data.";
  }
  switch (state.status) {
    case 'syncing':
      return 'Syncing with Strava…';
    case 'rateLimited': {
      const time = state.retryAfter ? formatDate(state.retryAfter, 'HH:mm', LOCALE) : 'a while';
      return `Strava rate limit reached. Showing saved data; syncing resumes after ${time}.`;
    }
    case 'stravaUnavailable':
      return state.lastSyncedAt
        ? `Couldn't sync with Strava. Showing data saved at ${formatDate(state.lastSyncedAt, 'd MMM y, HH:mm', LOCALE)}.`
        : "Couldn't sync with Strava. No data saved yet.";
    case 'partial':
      return `Still importing runs from Strava (${state.runsPendingDetail} remaining). This continues automatically.`;
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
  readonly state = input.required<SyncBannerState>();
  protected readonly text = computed(() => syncBannerText(this.state()));
}
