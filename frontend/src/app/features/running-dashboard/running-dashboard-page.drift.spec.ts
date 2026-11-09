import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { dashboard, stubCharts } from '../../testing/dashboard-fixtures';
import { stubPlanSection } from '../../testing/plan-section-stub';
import { stubRaceDayFormSection } from '../../testing/race-day-form-stub';
import { stubSyncStore } from '../../testing/sync-store-stub';
import { stubTrainingLoadSection } from '../../testing/training-load-stub';
import { stubEfficiencyFactorSection } from '../../testing/efficiency-factor-stub';
import { stubVo2maxSection } from '../../testing/vo2max-stub';
import { stubMarathonPaceSection } from '../../testing/marathon-pace-stub';
import { stubRingsSection } from '../../testing/rings-stub';
import { stubWellnessSection } from '../../testing/wellness-stub';
import { stubWeeklySummaryBell } from '../../testing/weekly-summary-stub';
import { RunningDashboardPage } from './running-dashboard-page';

describe('RunningDashboardPage with the long-run drift card', () => {
  let fixture: ComponentFixture<RunningDashboardPage>;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    stubCharts();
    stubVo2maxSection();
    stubEfficiencyFactorSection();
    stubRaceDayFormSection();
    stubWellnessSection();
    stubRingsSection();
    stubSyncStore();
    stubWeeklySummaryBell();
    stubTrainingLoadSection();
    stubMarathonPaceSection();
    stubPlanSection();
    fixture = TestBed.createComponent(RunningDashboardPage);
    http = TestBed.inject(HttpTestingController);
    fixture.detectChanges();
  });

  it('R6 - drift card renders right after HR card', () => {
    http.expectOne('/api/dashboard/running').flush(dashboard());
    fixture.detectChanges();
    const cards = (fixture.nativeElement as HTMLElement).querySelector('.cards');
    const tags = Array.from(cards?.children ?? []).map((child) => child.tagName.toLowerCase());
    expect(tags).toEqual([
      'app-weekly-distance-chart',
      'app-heart-rate-trend-chart',
      'app-long-run-drift-section',
      'app-efficiency-factor-section',
      'app-workout-pace-comparison-chart',
    ]);
    expect(cards?.querySelector('app-long-run-drift-section h2')?.textContent).toBe(
      'Long-run HR drift',
    );
    http.expectOne('/api/long-run-drift');
  });
});
