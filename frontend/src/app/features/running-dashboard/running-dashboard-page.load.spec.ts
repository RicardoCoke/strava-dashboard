import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { dashboard, stubCharts } from '../../testing/dashboard-fixtures';
import { trainingLoad } from '../../testing/training-load-fixtures';
import { trainingPlan } from '../../testing/training-plan-fixtures';
import { stubSyncStore } from '../../testing/sync-store-stub';
import { stubVo2maxSection } from '../../testing/vo2max-stub';
import { RunningDashboardPage } from './running-dashboard-page';

describe('RunningDashboardPage with the training load card', () => {
  let fixture: ComponentFixture<RunningDashboardPage>;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    stubCharts();
    stubVo2maxSection();
    stubSyncStore();
    fixture = TestBed.createComponent(RunningDashboardPage);
    http = TestBed.inject(HttpTestingController);
    fixture.detectChanges();
  });

  const element = () => fixture.nativeElement as HTMLElement;
  const text = () => element().textContent?.replace(/\s+/g, ' ') ?? '';
  const tags = () => Array.from(element().children).map((child) => child.tagName.toLowerCase());

  it('005 R10 - training load card directly after marathon build', () => {
    expect(tags().slice(0, 4)).toEqual([
      'h1',
      'app-sync-banner',
      'app-calendar-like-build',
      'app-training-load-section',
    ]);
    http.expectOne('/api/dashboard/running').flush(dashboard());
    http.expectOne('/api/training-plan').flush(trainingPlan());
    http.expectOne('/api/training-load').flush(trainingLoad());
    fixture.detectChanges();
    const order = tags();
    expect(order.indexOf('app-training-load-section')).toBe(3);
  });

  it('005 R10 - one training load request', () => {
    const requests = [
      http.expectOne('/api/dashboard/running'),
      http.expectOne('/api/training-plan'),
      http.expectOne('/api/training-load'),
    ];
    expect(requests.map((request) => request.request.method)).toEqual(['GET', 'GET', 'GET']);
    requests[2].flush(trainingLoad());
    fixture.detectChanges();
    http.verify();
  });

  it('005 R10 - fails independently', () => {
    http
      .expectOne('/api/training-load')
      .flush({ error: 'internal_error', message: 'x' }, { status: 500, statusText: 'Error' });
    http.expectOne('/api/dashboard/running').flush(dashboard());
    http.expectOne('/api/training-plan').flush(trainingPlan());
    fixture.detectChanges();
    expect(text()).toContain("Couldn't load training load.");
    expect(element().querySelector('app-headline-tiles')).not.toBeNull();
    expect(element().querySelector('app-build-progress')).not.toBeNull();
  });

  it('005 R10 - shows its own state while the rest fails', () => {
    http
      .expectOne('/api/dashboard/running')
      .flush({ error: 'internal_error', message: 'x' }, { status: 500, statusText: 'Error' });
    http.expectOne('/api/training-load').flush(trainingLoad());
    fixture.detectChanges();
    expect(text()).toContain("Couldn't load your dashboard.");
    expect(element().querySelector('app-training-load-chart')).not.toBeNull();
    http.expectOne('/api/training-plan');
  });
});
