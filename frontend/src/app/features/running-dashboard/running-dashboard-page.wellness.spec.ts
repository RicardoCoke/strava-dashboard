import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { dashboard, stubCharts } from '../../testing/dashboard-fixtures';
import { stubEfficiencyFactorSection } from '../../testing/efficiency-factor-stub';
import { stubLongRunDriftSection } from '../../testing/long-run-drift-stub';
import { stubMarathonPaceSection } from '../../testing/marathon-pace-stub';
import { stubRaceDayFormSection } from '../../testing/race-day-form-stub';
import { stubSyncStore } from '../../testing/sync-store-stub';
import { trainingLoad } from '../../testing/training-load-fixtures';
import { trainingPlan } from '../../testing/training-plan-fixtures';
import { stubVo2maxSection } from '../../testing/vo2max-stub';
import { wellness } from '../../testing/wellness-fixtures';
import { RunningDashboardPage } from './running-dashboard-page';

describe('RunningDashboardPage with the resting HR & HRV card', () => {
  let fixture: ComponentFixture<RunningDashboardPage>;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    stubCharts();
    stubVo2maxSection();
    stubRaceDayFormSection();
    stubLongRunDriftSection();
    stubEfficiencyFactorSection();
    stubMarathonPaceSection();
    stubSyncStore();
    fixture = TestBed.createComponent(RunningDashboardPage);
    http = TestBed.inject(HttpTestingController);
    fixture.detectChanges();
  });

  const element = () => fixture.nativeElement as HTMLElement;
  const text = () => element().textContent?.replace(/\s+/g, ' ') ?? '';
  const tags = () => Array.from(element().children).map((child) => child.tagName.toLowerCase());

  it('R9 - wellness card sits after training load and race-day form', () => {
    const order = tags();
    expect(order.indexOf('app-wellness-section')).toBe(
      order.indexOf('app-race-day-form-section') + 1,
    );
    http.expectOne('/api/dashboard/running').flush(dashboard());
    http.expectOne('/api/training-plan').flush(trainingPlan());
    http.expectOne('/api/training-load').flush(trainingLoad());
    http.expectOne('/api/wellness').flush(wellness());
    fixture.detectChanges();
    expect(element().querySelector('app-wellness-chart')).not.toBeNull();
    http.verify();
  });

  it('R1 - not connected card leaves the other cards working', () => {
    http
      .expectOne('/api/wellness')
      .flush(
        { error: 'wellness_not_configured', message: 'Intervals.icu is not connected.' },
        { status: 404, statusText: 'Not Found' },
      );
    http.expectOne('/api/dashboard/running').flush(dashboard());
    http.expectOne('/api/training-plan').flush(trainingPlan());
    http.expectOne('/api/training-load').flush(trainingLoad());
    fixture.detectChanges();
    expect(text()).toContain('Intervals.icu not connected.');
    expect(element().querySelector('app-headline-tiles')).not.toBeNull();
    expect(element().querySelector('app-training-load-chart')).not.toBeNull();
  });
});
