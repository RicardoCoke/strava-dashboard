import { HttpClient, provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { apiErrorInterceptor } from './api-error.interceptor';

describe('apiErrorInterceptor', () => {
  let http: HttpClient;
  let controller: HttpTestingController;
  let navigate: ReturnType<typeof vi.spyOn>;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideRouter([]),
        provideHttpClient(withInterceptors([apiErrorInterceptor])),
        provideHttpClientTesting(),
      ],
    });
    http = TestBed.inject(HttpClient);
    controller = TestBed.inject(HttpTestingController);
    navigate = vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);
  });

  function failWith(url: string, status: number, body: object | null): unknown {
    let failure: unknown;
    http.get(url).subscribe({ error: (error: unknown) => (failure = error) });
    controller.expectOne(url).flush(body, { status, statusText: 'Error' });
    return failure;
  }

  it('R2 - 401 from api navigates to connect', () => {
    const error = failWith('/api/dashboard/running', 401, { error: 'not_connected', message: 'x' });
    expect(navigate).toHaveBeenCalledWith(['/connect'], {});
    expect(error).toBeTruthy();
  });

  it('R3 - reauth error navigates to connect with reason', () => {
    failWith('/api/dashboard/running', 401, { error: 'strava_reauth_required', message: 'x' });
    expect(navigate).toHaveBeenCalledWith(['/connect'], { queryParams: { reason: 'reauth' } });
  });

  it('passes other errors through without navigating', () => {
    failWith('/api/dashboard/running', 500, { error: 'internal_error', message: 'x' });
    failWith('/other', 401, null);
    expect(navigate).not.toHaveBeenCalled();
  });
});
