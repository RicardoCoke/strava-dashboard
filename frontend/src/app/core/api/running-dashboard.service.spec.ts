import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { dashboard } from '../../testing/dashboard-fixtures';
import { RunningDashboardResponse } from './running-dashboard.models';
import { RunningDashboardService } from './running-dashboard.service';

describe('RunningDashboardService', () => {
  it('R13 - service calls dashboard endpoint', () => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    const service = TestBed.inject(RunningDashboardService);
    const http = TestBed.inject(HttpTestingController);
    let received: RunningDashboardResponse | undefined;

    service.getDashboard().subscribe((data) => (received = data));
    const request = http.expectOne('/api/dashboard/running');
    expect(request.request.method).toBe('GET');
    request.flush(dashboard());

    expect(received?.weeklyDistance.weeks.length).toBe(2);
    http.verify();
  });
});
