import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { dashboard, stubCharts } from '../../testing/dashboard-fixtures';
import { trainingPlan } from '../../testing/training-plan-fixtures';
import { stubTrainingLoadSection } from '../../testing/training-load-stub';
import { stubRaceDayFormSection } from '../../testing/race-day-form-stub';
import { stubSyncStore } from '../../testing/sync-store-stub';
import { stubLongRunDriftSection } from '../../testing/long-run-drift-stub';
import { stubEfficiencyFactorSection } from '../../testing/efficiency-factor-stub';
import { stubVo2maxSection } from '../../testing/vo2max-stub';
import { stubMarathonPaceSection } from '../../testing/marathon-pace-stub';
import { stubRingsSection } from '../../testing/rings-stub';
import { stubWellnessSection } from '../../testing/wellness-stub';
import { stubWeeklySummaryBell } from '../../testing/weekly-summary-stub';
import { stubRunWeather } from '../../testing/run-weather-stub';
import { RunningDashboardPage } from './running-dashboard-page';

describe('RunningDashboardPage with the marathon build section', () => {
  let fixture: ComponentFixture<RunningDashboardPage>;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    stubRunWeather();
    stubCharts();
    stubVo2maxSection();
    stubEfficiencyFactorSection();
    stubLongRunDriftSection();
    stubWellnessSection();
    stubRingsSection();
    stubSyncStore();
    stubWeeklySummaryBell();
    stubTrainingLoadSection();
    stubMarathonPaceSection();
    stubRaceDayFormSection();
    fixture = TestBed.createComponent(RunningDashboardPage);
    http = TestBed.inject(HttpTestingController);
    fixture.detectChanges();
  });

  const element = () => fixture.nativeElement as HTMLElement;
  const text = () => element().textContent ?? '';

  it('004 R9 - plan loads in parallel with the dashboard', () => {
    const dashboardRequest = http.expectOne('/api/dashboard/running');
    const planRequest = http.expectOne('/api/training-plan');
    expect([dashboardRequest.request.method, planRequest.request.method]).toEqual(['GET', 'GET']);

    dashboardRequest.flush(dashboard());
    planRequest.flush(trainingPlan());
    fixture.detectChanges();
    const order = Array.from(element().children).map((child) => child.tagName.toLowerCase());
    // 020 AC8.1: the rings sit first under the header; headline tiles and weekly distance follow.
    expect(order.slice(0, 5)).toEqual([
      'header',
      'app-rings-section',
      'app-sync-banner',
      'app-headline-tiles',
      'div',
    ]);
    // 017 AC11.1: the weekly summary bell sits in the header, after the Running heading.
    const header = Array.from(element().querySelector('header')?.children ?? []);
    expect(header.map((child) => child.tagName.toLowerCase())).toEqual(['h1', 'div']);
    expect(header[1].querySelector('app-weekly-summary-bell')).not.toBeNull();
    expect(order.indexOf('app-headline-tiles')).toBeLessThan(
      order.indexOf('app-calendar-like-build'),
    );
    http.verify();
  });

  it('004 R9 - build calendar card is last and the plan is requested once', () => {
    http.expectOne('/api/dashboard/running').flush(dashboard());
    http.expectOne('/api/training-plan').flush(trainingPlan());
    fixture.detectChanges();
    http.verify();

    const last = element().lastElementChild;
    expect(last?.tagName.toLowerCase()).toBe('app-build-calendar-section');
    const paceChart = element().querySelector('app-workout-pace-comparison-chart');
    expect(
      paceChart && last
        ? paceChart.compareDocumentPosition(last) & Node.DOCUMENT_POSITION_FOLLOWING
        : 0,
    ).toBeTruthy();
  });

  it('004 R11 - paces in the calendar card, without plan notes (010 R10)', () => {
    http.expectOne('/api/dashboard/running').flush(dashboard());
    http.expectOne('/api/training-plan').flush(trainingPlan());
    fixture.detectChanges();

    const bottom = element().querySelector('app-build-calendar-section');
    expect(bottom?.querySelector('h2')?.textContent).toBe('Build calendar');
    const bottomParts = Array.from(
      bottom?.querySelectorAll('app-target-paces, app-build-calendar') ?? [],
    ).map((el) => el.tagName.toLowerCase());
    expect(bottomParts).toEqual(['app-target-paces', 'app-build-calendar']);
    expect(bottom?.textContent).not.toContain('Plan notes');

    const top = element().querySelector('app-calendar-like-build');
    expect(top?.querySelector('app-target-paces, app-build-calendar')).toBeNull();
    expect(top?.querySelector('[role="progressbar"]')).not.toBeNull();
  });

  it('004 R13 - single retry button on error', () => {
    const bottom = () => element().querySelector('app-build-calendar-section');
    expect(bottom()?.children.length).toBe(0);
    http
      .expectOne('/api/training-plan')
      .flush(
        { error: 'training_plan_invalid', message: 'WEEK 3: VOLUME row missing' },
        { status: 422, statusText: 'Error' },
      );
    fixture.detectChanges();
    expect(bottom()?.children.length).toBe(0);
    expect(element().querySelectorAll('button.retry').length).toBe(1);
  });

  it('004 R16 - a new visit loads the plan again', () => {
    http.expectOne('/api/training-plan').flush(trainingPlan());
    fixture.destroy();
    const again = TestBed.createComponent(RunningDashboardPage);
    again.detectChanges();
    http.expectOne('/api/training-plan');
  });

  it('004 R9 - plan error does not affect running dashboard', () => {
    http
      .expectOne('/api/training-plan')
      .flush(
        { error: 'training_plan_unavailable', message: 'x' },
        { status: 503, statusText: 'Error' },
      );
    http.expectOne('/api/dashboard/running').flush(dashboard());
    fixture.detectChanges();
    expect(text()).toContain("Couldn't read your training plan file.");
    expect(text()).toContain('Weekly distance');
    expect(text()).not.toContain("Couldn't load your dashboard.");
  });

  it('004 R9 - dashboard error does not affect plan', () => {
    http
      .expectOne('/api/dashboard/running')
      .flush(null, { status: 500, statusText: 'Server Error' });
    http.expectOne('/api/training-plan').flush(trainingPlan());
    fixture.detectChanges();
    expect(text()).toContain("Couldn't load your dashboard.");
    expect(element().querySelector('[role="progressbar"]')).not.toBeNull();
  });

  it('004 R9 - dashboard still loading while plan renders', () => {
    http.expectOne('/api/training-plan').flush(trainingPlan());
    fixture.detectChanges();
    expect(text()).toContain('Loading your runs…');
    expect(element().querySelector('[role="progressbar"]')).not.toBeNull();
  });
});
