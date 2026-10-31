import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { dashboard, emptyDashboard, stubCharts } from '../../testing/dashboard-fixtures';
import { trainingLoad } from '../../testing/training-load-fixtures';
import { trainingPlan } from '../../testing/training-plan-fixtures';
import { vo2max } from '../../testing/vo2max-fixtures';
import { SyncStoreStub, stubSyncStore, syncStatus } from '../../testing/sync-store-stub';
import { RunningDashboardPage } from './running-dashboard-page';

describe('RunningDashboardPage with the VO2max card', () => {
  let fixture: ComponentFixture<RunningDashboardPage>;
  let http: HttpTestingController;
  let sync: SyncStoreStub;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    stubCharts();
    sync = stubSyncStore();
    fixture = TestBed.createComponent(RunningDashboardPage);
    http = TestBed.inject(HttpTestingController);
    fixture.detectChanges();
  });

  const element = () => fixture.nativeElement as HTMLElement;
  const text = () => element().textContent?.replace(/\s+/g, ' ') ?? '';
  const tags = () => Array.from(element().children).map((child) => child.tagName.toLowerCase());

  it('008 R10 - vo2max card directly after training load', () => {
    expect(tags().slice(0, 5)).toEqual([
      'h1',
      'app-sync-banner',
      'app-calendar-like-build',
      'app-training-load-section',
      'app-vo2max-section',
    ]);
  });

  it('008 R10 - one vo2max request alongside the others', () => {
    const requests = [
      http.expectOne('/api/dashboard/running'),
      http.expectOne('/api/training-plan'),
      http.expectOne('/api/training-load'),
      http.expectOne('/api/vo2max'),
    ];
    expect(requests.map((request) => request.request.method)).toEqual(['GET', 'GET', 'GET', 'GET']);
    requests[3].flush(vo2max());
    fixture.detectChanges();
    http.verify();
  });

  it('008 R10 - fails independently', () => {
    http
      .expectOne('/api/vo2max')
      .flush({ error: 'internal_error', message: 'x' }, { status: 500, statusText: 'Error' });
    http.expectOne('/api/dashboard/running').flush(dashboard());
    http.expectOne('/api/training-plan').flush(trainingPlan());
    http.expectOne('/api/training-load').flush(trainingLoad());
    fixture.detectChanges();
    expect(text()).toContain("Couldn't load VO2max.");
    expect(element().querySelector('app-headline-tiles')).not.toBeNull();
    expect(element().querySelector('app-training-load-chart')).not.toBeNull();
  });

  function flushAll(): void {
    http.expectOne('/api/dashboard/running').flush(dashboard());
    http.expectOne('/api/training-plan').flush(trainingPlan());
    http.expectOne('/api/training-load').flush(trainingLoad());
    http.expectOne('/api/vo2max').flush(vo2max());
    fixture.detectChanges();
  }

  it('009 R7 - status check starts alongside the card requests', () => {
    expect(sync.started).toBe(true);
    flushAll();
  });

  it('009 R9 - cards refresh quietly after sync without loading state', () => {
    flushAll();
    sync.finish();
    fixture.detectChanges();

    const reloads = [
      http.expectOne('/api/dashboard/running'),
      http.expectOne('/api/training-load'),
      http.expectOne('/api/vo2max'),
    ];
    expect(element().querySelector('[role="status"] app-ring-spinner, .loading')).toBeNull();
    expect(text()).not.toContain('Loading VO2max…');
    expect(element().querySelector('app-weekly-distance-chart')).not.toBeNull();
    reloads[0].flush(dashboard());
    reloads[1].flush(trainingLoad());
    reloads[2].flush(vo2max());
    fixture.detectChanges();
    http.verify();
  });

  it('009 R9 - failed quiet refresh keeps previous content', () => {
    flushAll();
    sync.finish();
    fixture.detectChanges();
    const error = { status: 500, statusText: 'Error' };
    http.expectOne('/api/dashboard/running').flush(null, error);
    http.expectOne('/api/training-load').flush(null, error);
    http.expectOne('/api/vo2max').flush(null, error);
    fixture.detectChanges();

    expect(text()).not.toContain("Couldn't load");
    expect(element().querySelector('app-weekly-distance-chart')).not.toBeNull();
    expect(element().querySelector('app-training-load-chart')).not.toBeNull();
    expect(element().querySelector('app-vo2max-chart')).not.toBeNull();
  });

  it('009 R9 - quiet refresh keeps selected heart rate type', () => {
    flushAll();
    const select = element().querySelector<HTMLSelectElement>('app-heart-rate-trend-chart select');
    const last = select?.options[select.options.length - 1]?.value ?? '';
    if (select) {
      select.value = last;
      select.dispatchEvent(new Event('change'));
    }
    fixture.detectChanges();

    sync.finish();
    fixture.detectChanges();
    http.expectOne('/api/dashboard/running').flush(dashboard());
    http.expectOne('/api/training-load').flush(trainingLoad());
    http.expectOne('/api/vo2max').flush(vo2max());
    fixture.detectChanges();

    expect(
      element().querySelector<HTMLSelectElement>('app-heart-rate-trend-chart select')?.value,
    ).toBe(last);
  });

  it('009 R9 - first import fills empty cards after first batch', () => {
    http.expectOne('/api/dashboard/running').flush(emptyDashboard());
    http.expectOne('/api/training-plan').flush(trainingPlan());
    http.expectOne('/api/training-load').flush(trainingLoad());
    http.expectOne('/api/vo2max').flush(vo2max());
    sync.status.set(syncStatus({ status: 'syncing', nextSyncInSeconds: null }));
    fixture.detectChanges();
    expect(text()).toContain('No runs in the last 5 months');
    expect(text()).toContain('Syncing with Strava…');

    sync.finish();
    fixture.detectChanges();
    http.expectOne('/api/dashboard/running').flush(dashboard());
    http.expectOne('/api/training-load').flush(trainingLoad());
    http.expectOne('/api/vo2max').flush(vo2max());
    fixture.detectChanges();

    expect(text()).not.toContain('No runs in the last 5 months');
    expect(text()).not.toContain('Syncing with Strava…');
    expect(element().querySelector('app-weekly-distance-chart')).not.toBeNull();
  });
});
