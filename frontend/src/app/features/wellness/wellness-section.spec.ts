import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { WellnessResponse } from '../../core/api/wellness.models';
import { PersonStore } from '../../core/person/person.store';
import { SyncStore } from '../../core/sync/sync.store';
import { stubCharts } from '../../testing/dashboard-fixtures';
import { SyncStoreStub } from '../../testing/sync-store-stub';
import { emptyWellness, wellness } from '../../testing/wellness-fixtures';
import { WellnessSection } from './wellness-section';

describe('WellnessSection', () => {
  let fixture: ComponentFixture<WellnessSection>;
  let http: HttpTestingController;
  let sync: SyncStoreStub;

  beforeEach(() => {
    sync = new SyncStoreStub();
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: SyncStore, useValue: sync },
      ],
    });
    stubCharts();
    fixture = TestBed.createComponent(WellnessSection);
    http = TestBed.inject(HttpTestingController);
    fixture.detectChanges();
  });

  const element = () => fixture.nativeElement as HTMLElement;
  const text = () => element().textContent?.replace(/\s+/g, ' ') ?? '';

  function respond(data: WellnessResponse): void {
    http.expectOne('/api/wellness').flush(data);
    fixture.detectChanges();
  }

  function fail(status: number, body: object | null): void {
    http.expectOne('/api/wellness').flush(body, { status, statusText: 'Error' });
    fixture.detectChanges();
  }

  it('R9 - shows loading state', () => {
    expect(element().querySelector('h2')?.textContent).toBe('Resting HR & HRV');
    expect(element().querySelector('[role="status"]')?.textContent).toContain(
      'Loading resting HR & HRV…',
    );
    expect(element().querySelector('app-wellness-chart')).toBeNull();
  });

  it('019 R11 - card hidden for marina', () => {
    TestBed.inject(PersonStore).select('marina');
    fail(404, { error: 'wellness_not_configured', message: 'Garmin Connect is not connected.' });
    expect(element().querySelector('section')).toBeNull();
    localStorage.clear();
  });

  it('R1 - shows not connected message on 404', () => {
    fail(404, { error: 'wellness_not_configured', message: 'Garmin Connect is not connected.' });
    expect(text()).toContain('Garmin Connect not connected.');
    expect(text()).toContain('Set GARMIN_API_KEY in .env, then restart the backend.');
    expect(element().querySelector('app-wellness-chart')).toBeNull();
    expect(element().querySelector('button')).toBeNull();
  });

  it('R9 - shows error with retry', () => {
    fail(500, { error: 'internal_error', message: 'Something went wrong.' });
    expect(element().querySelector('[role="alert"]')?.textContent).toContain(
      "Couldn't load resting HR & HRV.",
    );
    element().querySelector<HTMLButtonElement>('button.retry')?.click();
    fixture.detectChanges();
    expect(text()).toContain('Loading resting HR & HRV…');
    respond(wellness());
    expect(element().querySelector('app-wellness-chart')).not.toBeNull();
  });

  it('R9 - unexpected body shows the error state', () => {
    http.expectOne('/api/wellness').flush({ days: 'nope' });
    fixture.detectChanges();
    expect(text()).toContain("Couldn't load resting HR & HRV.");
  });

  it('R9 - empty text per source status', () => {
    respond(emptyWellness());
    expect(text()).toContain('Waiting for the first sync from Garmin Connect.');
    expect(element().querySelector('app-wellness-chart')).toBeNull();
    expect(element().querySelector('app-wellness-latest')).toBeNull();
  });

  it('R9 - shows tiles, note, chart and footnote', () => {
    respond(wellness());
    const tiles = Array.from(element().querySelectorAll('.tile')).map((tile) =>
      Array.from(tile.children).map((part) => part.textContent?.trim()),
    );
    expect(tiles).toEqual([
      ['Resting HR', '53 bpm', 'Above baseline', '+5.6 bpm vs baseline', 'on Sat 31 Oct'],
      ['HRV', '58 ms', '−4% vs baseline', 'on Fri 30 Oct'],
    ]);
    expect(element().querySelector('.wellness-note')?.textContent).toBe(
      'Resting HR above baseline while load is high',
    );
    expect(element().querySelector('.chart-stub')?.getAttribute('aria-label')).toBe(
      'Resting heart rate and HRV per day with 7-day baselines, last 12 weeks',
    );
    expect(text()).toContain('From Garmin Connect · dashed line = average of the previous 7 days');
    expect(element().querySelector('.wellness-stale')).toBeNull();
  });

  it('R6 - flagged tile shows text and warning colour', () => {
    respond(wellness());
    const tiles = element().querySelectorAll('.tile');
    expect(tiles[0].classList).toContain('flagged');
    expect(tiles[0].querySelector('.tile-flag')?.textContent).toBe('Above baseline');
    expect(tiles[1].classList).not.toContain('flagged');
    expect(tiles[1].querySelector('.tile-flag')).toBeNull();
  });

  it('R9 - tile without a reading and no note', () => {
    respond(
      wellness({
        latest: { restingHr: null, hrv: wellness().latest.hrv },
        readiness: { note: null, loadHigh: null },
      }),
    );
    expect(element().querySelector('.tile')?.textContent).toContain('No reading');
    expect(element().querySelector('.wellness-note')).toBeNull();
  });

  it('R9 - shows stale line when last fetch failed', () => {
    respond(wellness({ source: { status: 'unavailable', lastFetchedAt: null } }));
    expect(element().querySelector('.wellness-stale')?.textContent).toBe(
      "Showing saved data: Garmin Connect couldn't be reached at the last sync.",
    );
    expect(element().querySelector('app-wellness-chart')).not.toBeNull();
  });

  it('R9 - reloads quietly when dataVersion changes', () => {
    respond(wellness());
    sync.finish();
    fixture.detectChanges();

    const reload = http.expectOne('/api/wellness');
    expect(element().querySelector('[role="status"]')).toBeNull();
    expect(element().querySelector('app-wellness-chart')).not.toBeNull();
    reload.flush({ error: 'internal_error', message: 'x' }, { status: 500, statusText: 'Error' });
    fixture.detectChanges();
    expect(element().querySelector('[role="alert"]')).toBeNull();
    expect(element().querySelector('app-wellness-chart')).not.toBeNull();
    http.verify();
  });

  it('R10 - card uses theme tokens only', () => {
    respond(wellness());
    expect(element().querySelector('section')?.classList).toContain('card');
    const cardStyles = Array.from(document.querySelectorAll('style'))
      .map((style) => style.textContent ?? '')
      .filter((css) => css.includes('.wellness-note') || css.includes('.tile-flag'));
    expect(cardStyles.length).toBeGreaterThan(0);
    cardStyles.forEach((css) => {
      expect(css).toContain('var(--color-');
      expect(css).not.toMatch(/#[0-9a-f]{3,8}\b|rgba?\(/i);
    });
  });
});
