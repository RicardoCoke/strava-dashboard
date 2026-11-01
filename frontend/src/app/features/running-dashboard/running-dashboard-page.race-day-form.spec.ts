import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { dashboard, stubCharts } from '../../testing/dashboard-fixtures';
import { stubPlanSection } from '../../testing/plan-section-stub';
import { raceDayForm } from '../../testing/race-day-form-fixtures';
import { stubSyncStore } from '../../testing/sync-store-stub';
import { stubTrainingLoadSection } from '../../testing/training-load-stub';
import { stubVo2maxSection } from '../../testing/vo2max-stub';
import { stubMarathonPaceSection } from '../../testing/marathon-pace-stub';
import { RunningDashboardPage } from './running-dashboard-page';

/** The page with the real race-day form card (feature 011, R12). */
describe('RunningDashboardPage with the race-day form card', () => {
  let fixture: ComponentFixture<RunningDashboardPage>;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    stubCharts();
    stubVo2maxSection();
    stubTrainingLoadSection();
    stubMarathonPaceSection();
    stubPlanSection();
    stubSyncStore();
    fixture = TestBed.createComponent(RunningDashboardPage);
    http = TestBed.inject(HttpTestingController);
    fixture.detectChanges();
  });

  const element = () => fixture.nativeElement as HTMLElement;
  const text = () => element().textContent?.replace(/\s+/g, ' ') ?? '';

  it('R12 - one race-day request', () => {
    const request = http.expectOne('/api/race-day-form');
    expect(request.request.method).toBe('GET');
    request.flush(raceDayForm());
    http.expectOne('/api/dashboard/running').flush(dashboard());
    fixture.detectChanges();
    expect(element().querySelector('app-race-day-form-section .race-tile')).not.toBeNull();
    http.verify();
  });

  it('R12 - fails independently', () => {
    http
      .expectOne('/api/race-day-form')
      .flush({ error: 'internal_error', message: 'x' }, { status: 500, statusText: 'Error' });
    http.expectOne('/api/dashboard/running').flush(dashboard());
    fixture.detectChanges();
    expect(text()).toContain("Couldn't load race-day form.");
    expect(element().querySelector('app-headline-tiles')).not.toBeNull();
    expect(element().querySelector('app-weekly-distance-chart')).not.toBeNull();
  });

  it('R12 - card renders its own state while the dashboard fails', () => {
    http.expectOne('/api/dashboard/running').flush(null, { status: 500, statusText: 'Error' });
    http.expectOne('/api/race-day-form').flush(raceDayForm());
    fixture.detectChanges();
    expect(text()).toContain("Couldn't load your dashboard.");
    expect(element().querySelector('app-race-day-form-section .race-tile')).not.toBeNull();
  });
});
