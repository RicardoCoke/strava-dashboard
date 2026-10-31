import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { dashboard, emptyDashboard, stubCharts } from '../../testing/dashboard-fixtures';
import { stubPlanSection } from '../../testing/plan-section-stub';
import { stubTrainingLoadSection } from '../../testing/training-load-stub';
import { RunningDashboardPage } from './running-dashboard-page';

describe('RunningDashboardPage', () => {
  let fixture: ComponentFixture<RunningDashboardPage>;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    stubCharts();
    stubTrainingLoadSection();
    stubPlanSection();
    fixture = TestBed.createComponent(RunningDashboardPage);
    http = TestBed.inject(HttpTestingController);
    fixture.detectChanges();
  });

  const text = () => (fixture.nativeElement as HTMLElement).textContent ?? '';
  const element = () => fixture.nativeElement as HTMLElement;

  it('R13 - shows loading state', () => {
    expect(text()).toContain('Loading your runs…');
    expect(text()).toContain('The first import can take a minute.');
    expect(element().querySelector('app-chart')).toBeNull();
  });

  it('R13 - shows error state and retries', () => {
    http
      .expectOne('/api/dashboard/running')
      .flush(null, { status: 500, statusText: 'Server Error' });
    fixture.detectChanges();
    expect(text()).toContain("Couldn't load your dashboard.");

    element().querySelector('button')?.click();
    fixture.detectChanges();
    http.expectOne('/api/dashboard/running').flush(dashboard());
    fixture.detectChanges();
    expect(text()).toContain('Weekly distance');
  });

  it('R13 - shows page empty state when no runs', () => {
    http.expectOne('/api/dashboard/running').flush(emptyDashboard());
    fixture.detectChanges();
    expect(text()).toContain('No runs in the last 5 months');
    expect(element().querySelectorAll('section.card').length).toBe(0);
  });

  it('R13 - renders three cards in order', () => {
    http.expectOne('/api/dashboard/running').flush(dashboard());
    fixture.detectChanges();
    const headings = Array.from(element().querySelectorAll('section.card h2')).map((h) =>
      h.textContent?.trim(),
    );
    expect(headings).toEqual([
      'Weekly distance',
      'Heart rate by run type',
      'Workout pace: last 30 days vs previous 30 days',
    ]);
  });

  it('R5 - sync banner shows above the charts when status is not ok', () => {
    http.expectOne('/api/dashboard/running').flush(
      dashboard({
        sync: { status: 'partial', lastSyncedAt: null, retryAfter: null, runsPendingDetail: 12 },
      }),
    );
    fixture.detectChanges();
    expect(text()).toContain('Still importing runs from Strava (12 remaining).');
  });
});
