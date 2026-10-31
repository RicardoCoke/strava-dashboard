import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { dashboard, stubCharts } from '../../testing/dashboard-fixtures';
import { trainingLoad } from '../../testing/training-load-fixtures';
import { trainingPlan } from '../../testing/training-plan-fixtures';
import { vo2max } from '../../testing/vo2max-fixtures';
import { RunningDashboardPage } from './running-dashboard-page';

describe('RunningDashboardPage with the VO2max card', () => {
  let fixture: ComponentFixture<RunningDashboardPage>;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    stubCharts();
    fixture = TestBed.createComponent(RunningDashboardPage);
    http = TestBed.inject(HttpTestingController);
    fixture.detectChanges();
  });

  const element = () => fixture.nativeElement as HTMLElement;
  const text = () => element().textContent?.replace(/\s+/g, ' ') ?? '';
  const tags = () => Array.from(element().children).map((child) => child.tagName.toLowerCase());

  it('008 R10 - vo2max card directly after training load', () => {
    expect(tags().slice(0, 4)).toEqual([
      'h1',
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
});
