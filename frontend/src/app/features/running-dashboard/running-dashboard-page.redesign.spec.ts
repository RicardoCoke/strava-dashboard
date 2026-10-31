import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { dashboard, emptyDashboard, hrType, stubCharts } from '../../testing/dashboard-fixtures';
import { stubPlanSection } from '../../testing/plan-section-stub';
import { RunningDashboardPage } from './running-dashboard-page';

const URL = '/api/dashboard/running';

describe('RunningDashboardPage redesign', () => {
  let fixture: ComponentFixture<RunningDashboardPage>;
  let http: HttpTestingController;
  const element = () => fixture.nativeElement as HTMLElement;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    stubCharts();
    stubPlanSection();
    fixture = TestBed.createComponent(RunningDashboardPage);
    http = TestBed.inject(HttpTestingController);
    fixture.detectChanges();
  });

  function respond(body: object | null, status = 200): void {
    http.expectOne(URL).flush(body, { status, statusText: status === 200 ? 'OK' : 'Error' });
    fixture.detectChanges();
  }

  it('002 R3 - loading shows ring spinner above loading text and note', () => {
    const loading = element().querySelector('.loading');
    const children = Array.from(loading?.children ?? []).map(
      (c) => c.tagName.toLowerCase() + '|' + c.textContent?.trim(),
    );
    expect(children).toEqual([
      'app-ring-spinner|',
      'p|Loading your runs…',
      'p|The first import can take a minute.',
    ]);
    expect(element().querySelector('app-headline-tiles, section.card')).toBeNull();
  });

  it('002 R3 - spinner renders track and accent arc', () => {
    const circles = element().querySelectorAll('app-ring-spinner svg circle');
    expect(Array.from(circles).map((c) => c.getAttribute('class'))).toEqual(['track', 'arc']);
    const svg = element().querySelector('app-ring-spinner svg');
    expect([svg?.getAttribute('width'), svg?.getAttribute('height')]).toEqual(['64', '64']);
  });

  it('002 R3 - loading state is a polite live region and spinner is aria hidden', () => {
    const loading = element().querySelector('.loading');
    expect(loading?.getAttribute('role')).toBe('status');
    expect(loading?.getAttribute('aria-live')).toBe('polite');
    expect(element().querySelector('app-ring-spinner svg')?.getAttribute('aria-hidden')).toBe(
      'true',
    );
  });

  it('002 R3 - spinner removed on response and shown again on retry', () => {
    respond(null, 500);
    expect(element().querySelector('app-ring-spinner')).toBeNull();
    element().querySelector<HTMLButtonElement>('button.retry')?.click();
    fixture.detectChanges();
    expect(element().querySelector('app-ring-spinner')).not.toBeNull();
    respond(dashboard());
    expect(element().querySelector('app-ring-spinner')).toBeNull();
  });

  it('002 R2 - error state shows alert card and retry button', () => {
    respond(null, 500);
    const card = element().querySelector('.card[role="alert"]');
    expect(card?.textContent).toContain("Couldn't load your dashboard.");
    expect(card?.querySelector('button.retry')?.textContent?.trim()).toBe('Retry');
  });

  it('002 R4 - three tiles render between banner and weekly card in order', () => {
    respond(
      dashboard({
        sync: { status: 'partial', lastSyncedAt: null, retryAfter: null, runsPendingDetail: 3 },
      }),
    );
    const order = Array.from(
      element().querySelectorAll('app-sync-banner, app-headline-tiles, app-weekly-distance-chart'),
    ).map((e) => e.tagName.toLowerCase());
    expect(order).toEqual(['app-sync-banner', 'app-headline-tiles', 'app-weekly-distance-chart']);
    const labels = Array.from(element().querySelectorAll('.tile .label')).map((l) =>
      l.textContent?.trim(),
    );
    expect(labels).toEqual(['This week', 'Easy-run HR trend', 'Workout pace']);
  });

  it('002 R4 - tile shows label value unit and detail', () => {
    respond(dashboard());
    const week = element().querySelector('[data-tile="thisWeek"]');
    expect(week?.querySelector('.value')?.textContent?.trim()).toBe('32.4');
    expect(week?.querySelector('.unit')?.textContent?.trim()).toBe('km');
    expect(week?.querySelector('.detail')?.textContent?.trim()).toBe('4 runs');
  });

  it('002 R8 - tile value has tone class', () => {
    respond(dashboard());
    const classOf = (id: string) =>
      element().querySelector(`[data-tile="${id}"] .value`)?.className;
    expect(classOf('thisWeek')).toContain('tone-neutral');
    expect(classOf('easyHrTrend')).toContain('tone-better');
    expect(classOf('workoutPace')).toContain('tone-better');
  });

  it('002 R4 - no tiles in error state', () => {
    respond(null, 500);
    expect(element().querySelector('app-headline-tiles')).toBeNull();
  });

  it('002 R4 - no tiles in loading or empty state', () => {
    expect(element().querySelector('app-headline-tiles')).toBeNull();
    respond(emptyDashboard());
    expect(element().querySelector('app-headline-tiles')).toBeNull();
    expect(element().querySelector('.card .empty')?.textContent).toContain(
      'No runs in the last 5 months',
    );
  });

  it('002 R4 - tiles make no extra request', () => {
    respond(dashboard());
    http.expectNone(URL);
    http.verify();
  });

  it('002 R6 - hr selector does not change tile', () => {
    const base = dashboard();
    respond({
      ...base,
      heartRateTrend: {
        ...base.heartRateTrend,
        types: [
          hrType(),
          hrType({
            typeKey: '5x6min',
            label: '5x6min',
            kind: 'workout',
            trend: { ...hrType().trend!, changeBpm: 4 },
          }),
        ],
      },
    });
    const select = element().querySelector('select') as HTMLSelectElement;
    select.value = '5x6min';
    select.dispatchEvent(new Event('change'));
    fixture.detectChanges();
    expect(element().querySelector('[data-tile="easyHrTrend"] .value')?.textContent?.trim()).toBe(
      '−8.0',
    );
  });

  it('002 R2 - card titles keep their text', () => {
    respond(dashboard());
    const titles = Array.from(element().querySelectorAll('section.card h2')).map((h) =>
      h.textContent?.trim(),
    );
    expect(titles).toEqual([
      'Weekly distance',
      'Heart rate by run type',
      'Workout pace: last 30 days vs previous 30 days',
    ]);
  });

  it('002 R2 - sync banner uses notice style and keeps texts', () => {
    respond(
      dashboard({
        sync: { status: 'partial', lastSyncedAt: null, retryAfter: null, runsPendingDetail: 3 },
      }),
    );
    const banner = element().querySelector('.banner');
    expect(banner?.classList).toContain('notice');
    expect(banner?.getAttribute('role')).toBe('status');
    expect(banner?.textContent).toContain('Still importing runs from Strava (3 remaining).');
  });

  it('002 R8 - pace and hr card change texts have tone class', () => {
    respond(dashboard());
    expect(element().querySelector('app-heart-rate-trend-chart .note')?.className).toContain(
      'tone-better',
    );
    expect(
      element().querySelector('app-workout-pace-comparison-chart .change')?.className,
    ).toContain('tone-better');
  });
});
