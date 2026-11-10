import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { App } from './app';
import { routes } from './app.routes';
import { stubCharts } from './testing/dashboard-fixtures';

describe('App', () => {
  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [App],
      providers: [provideRouter(routes), provideHttpClient(), provideHttpClientTesting()],
    });
    stubCharts();
  });

  it('R1 - app shell shows Powered by Strava', async () => {
    const fixture = TestBed.createComponent(App);
    await fixture.whenStable();
    expect((fixture.nativeElement as HTMLElement).querySelector('footer')?.textContent).toContain(
      'Powered by Strava',
    );
  });

  it('R1 - root route shows running dashboard, not placeholder', async () => {
    const harness = await RouterTestingHarness.create();
    await harness.navigateByUrl('/');
    const element = harness.routeNativeElement as HTMLElement;
    expect(element.querySelector('h1')?.textContent).toBe('Running');
    expect(element.textContent).not.toContain('Hello, frontend');
    expect(element.textContent).not.toContain('Explore the Docs');
    TestBed.inject(HttpTestingController).expectOne('/api/dashboard/running');
  });

  it('023 R3 - /recovery shows the Recovery page', async () => {
    const harness = await RouterTestingHarness.create();
    await harness.navigateByUrl('/recovery');
    expect((harness.routeNativeElement as HTMLElement).querySelector('h1')?.textContent).toBe(
      'Recovery',
    );
    TestBed.inject(HttpTestingController).match(() => true);
  });

  it('R1 - unknown route redirects to dashboard', async () => {
    const harness = await RouterTestingHarness.create();
    await harness.navigateByUrl('/foo');
    expect(TestBed.inject(HttpTestingController).expectOne('/api/dashboard/running')).toBeTruthy();
    expect((harness.routeNativeElement as HTMLElement).querySelector('h1')?.textContent).toBe(
      'Running',
    );
  });

  it('021 R2 - /connect redirects to the dashboard, which keeps the footer', async () => {
    const fixture = TestBed.createComponent(App);
    const router = TestBed.inject(Router);
    await router.navigateByUrl('/connect');
    await fixture.whenStable();
    expect(router.url).toBe('/');
    const footer = (fixture.nativeElement as HTMLElement).querySelector('footer');
    expect(footer?.textContent).toContain('Powered by Strava');
    TestBed.inject(HttpTestingController).match('/api/dashboard/running');
  });
});
