import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { dashboard, emptyDashboard, paceType, stubCharts } from '../../testing/dashboard-fixtures';
import { stubPlanSection } from '../../testing/plan-section-stub';
import { stubTrainingLoadSection } from '../../testing/training-load-stub';
import { stubVo2maxSection } from '../../testing/vo2max-stub';
import { RunningDashboardPage } from './running-dashboard-page';

describe('RunningDashboardPage', () => {
  let fixture: ComponentFixture<RunningDashboardPage>;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    stubCharts();
    stubVo2maxSection();
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
    expect(headings).toEqual(['Weekly distance', 'Heart rate by run type', 'Workout pace by type']);
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

  it('010 R4 - selection kept after reload, falls back when gone', () => {
    const types = [paceType(), paceType({ typeKey: '4x8min', label: '4x8min' })];
    const withTypes = dashboard({
      workoutPaceComparison: { ...dashboard().workoutPaceComparison, types },
    });
    http.expectOne('/api/dashboard/running').flush(withTypes);
    fixture.detectChanges();
    const select = () =>
      element().querySelector<HTMLSelectElement>('app-workout-pace-comparison-chart select');
    const picker = select();
    if (picker) {
      picker.value = '4x8min';
      picker.dispatchEvent(new Event('change'));
    }
    fixture.detectChanges();

    // A failed load, then Retry: the page reloads and keeps the picked type.
    (fixture.componentInstance as unknown as { retry(): void }).retry();
    fixture.detectChanges();
    http.expectOne('/api/dashboard/running').flush(withTypes);
    fixture.detectChanges();
    expect(select()?.value).toBe('4x8min');

    (fixture.componentInstance as unknown as { retry(): void }).retry();
    fixture.detectChanges();
    http.expectOne('/api/dashboard/running').flush(dashboard());
    fixture.detectChanges();
    expect(select()?.value).toBe('5x6min');
  });
});
