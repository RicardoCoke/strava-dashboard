import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { dashboard, stubCharts } from '../../testing/dashboard-fixtures';
import { stubSyncStore } from '../../testing/sync-store-stub';
import { stubTrainingLoadSection } from '../../testing/training-load-stub';
import { trainingPlan } from '../../testing/training-plan-fixtures';
import { stubVo2maxSection } from '../../testing/vo2max-stub';
import { stubRaceDayFormSection } from '../../testing/race-day-form-stub';
import { stubEfficiencyFactorSection } from '../../testing/efficiency-factor-stub';
import { stubLongRunDriftSection } from '../../testing/long-run-drift-stub';
import { stubRingsSection } from '../../testing/rings-stub';
import { stubWellnessSection } from '../../testing/wellness-stub';
import { stubWeeklySummaryBell } from '../../testing/weekly-summary-stub';
import { stubRunWeather } from '../../testing/run-weather-stub';
import { RunningDashboardPage } from './running-dashboard-page';

describe('RunningDashboardPage with the marathon pace card', () => {
  let fixture: ComponentFixture<RunningDashboardPage>;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    stubRunWeather();
    stubCharts();
    stubVo2maxSection();
    stubSyncStore();
    stubWeeklySummaryBell();
    stubTrainingLoadSection();
    stubRaceDayFormSection();
    stubLongRunDriftSection();
    stubEfficiencyFactorSection();
    stubWellnessSection();
    stubRingsSection();
    fixture = TestBed.createComponent(RunningDashboardPage);
    http = TestBed.inject(HttpTestingController);
    fixture.detectChanges();
  });

  const element = () => fixture.nativeElement as HTMLElement;
  const follows = (first: Element | null, second: Element | null) =>
    first && second
      ? (first.compareDocumentPosition(second) & Node.DOCUMENT_POSITION_FOLLOWING) !== 0
      : false;

  it('R8 - card sits between workout pace and build calendar', () => {
    http.expectOne('/api/dashboard/running').flush(dashboard());
    http.expectOne('/api/training-plan').flush(trainingPlan());
    http.expectOne('/api/marathon-pace');
    fixture.detectChanges();

    const card = element().querySelector('app-marathon-pace-section');
    const paceChart = element().querySelector('app-workout-pace-comparison-chart');
    const calendar = element().querySelector('app-build-calendar-section');
    expect(follows(paceChart, card)).toBe(true);
    expect(follows(card, calendar)).toBe(true);
    expect(element().lastElementChild?.tagName.toLowerCase()).toBe('app-build-calendar-section');
  });

  it('R7 - card error does not affect the dashboard', () => {
    http.expectOne('/api/dashboard/running').flush(dashboard());
    http.expectOne('/api/training-plan').flush(trainingPlan());
    http.expectOne('/api/marathon-pace').flush(null, { status: 500, statusText: 'Error' });
    fixture.detectChanges();

    expect(element().querySelector('app-marathon-pace-section')?.textContent).toContain(
      "Couldn't load marathon pace.",
    );
    expect(element().querySelector('app-headline-tiles')).not.toBeNull();
    expect(element().querySelector('app-workout-pace-comparison-chart')).not.toBeNull();
    http.verify();
  });
});
