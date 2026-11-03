import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { dashboard, stubCharts } from '../../testing/dashboard-fixtures';
import { efficiencyFactor } from '../../testing/efficiency-factor-fixtures';
import { stubLongRunDriftSection } from '../../testing/long-run-drift-stub';
import { stubMarathonPaceSection } from '../../testing/marathon-pace-stub';
import { stubPlanSection } from '../../testing/plan-section-stub';
import { stubRaceDayFormSection } from '../../testing/race-day-form-stub';
import { stubSyncStore } from '../../testing/sync-store-stub';
import { stubTrainingLoadSection } from '../../testing/training-load-stub';
import { stubVo2maxSection } from '../../testing/vo2max-stub';
import { stubWellnessSection } from '../../testing/wellness-stub';
import { stubWeeklySummaryBell } from '../../testing/weekly-summary-stub';
import { RunningDashboardPage } from './running-dashboard-page';

describe('RunningDashboardPage with the efficiency factor card', () => {
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
    stubMarathonPaceSection();
    stubWellnessSection();
    stubSyncStore();
    stubWeeklySummaryBell();
    stubTrainingLoadSection();
    stubPlanSection();
    fixture = TestBed.createComponent(RunningDashboardPage);
    http = TestBed.inject(HttpTestingController);
    fixture.detectChanges();
  });

  it('014 R8 - efficiency card follows the HR cards and makes one request', () => {
    http.expectOne('/api/dashboard/running').flush(dashboard());
    fixture.detectChanges();
    const cards = (fixture.nativeElement as HTMLElement).querySelector('.cards');
    const tags = Array.from(cards?.children ?? []).map((child) => child.tagName.toLowerCase());
    expect(tags.indexOf('app-efficiency-factor-section')).toBe(
      tags.indexOf('app-long-run-drift-section') + 1,
    );
    expect(tags.indexOf('app-long-run-drift-section')).toBe(
      tags.indexOf('app-heart-rate-trend-chart') + 1,
    );

    http.expectOne('/api/efficiency-factor').flush(efficiencyFactor());
    fixture.detectChanges();
    expect(cards?.querySelector('app-efficiency-factor-section h2')?.textContent).toBe(
      'Efficiency factor',
    );
    http.verify();
  });
});
