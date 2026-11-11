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

  it('R1 - root route shows running dashboard, not placeholder', async () => {
    const harness = await RouterTestingHarness.create();
    await harness.navigateByUrl('/');
    const element = harness.routeNativeElement as HTMLElement;
    expect(element.querySelector('h1')).toBeNull();
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
    expect(harness.routeNativeElement).not.toBeNull();
  });

  it('021 R2 - /connect redirects to the dashboard, without a footer', async () => {
    const fixture = TestBed.createComponent(App);
    const router = TestBed.inject(Router);
    await router.navigateByUrl('/connect');
    await fixture.whenStable();
    expect(router.url).toBe('/');
    expect((fixture.nativeElement as HTMLElement).querySelector('footer')).toBeNull();
    TestBed.inject(HttpTestingController).match('/api/dashboard/running');
  });
});
