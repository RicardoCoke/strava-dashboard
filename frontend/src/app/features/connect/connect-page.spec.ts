import { TestBed } from '@angular/core/testing';
import { ConnectPage } from './connect-page';

describe('ConnectPage', () => {
  function render(inputs: { error?: string; reason?: string } = {}): HTMLElement {
    const fixture = TestBed.createComponent(ConnectPage);
    Object.entries(inputs).forEach(([key, value]) => fixture.componentRef.setInput(key, value));
    fixture.detectChanges();
    return fixture.nativeElement as HTMLElement;
  }

  it('R2 - connect page links to auth login', () => {
    const link = render().querySelector('a');
    expect(link?.getAttribute('href')).toBe('/auth/strava/login');
    expect(link?.textContent).toContain('Connect with Strava');
  });

  it('R2 - connect page shows fixed message per error code', () => {
    expect(render({ error: 'access_denied' }).textContent).toContain(
      'You declined access on Strava',
    );
    expect(render({ error: 'scope_missing' }).textContent).toContain('including private ones');
    expect(render({ error: 'state_mismatch' }).textContent).toContain('did not match');
    expect(render({ error: 'token_exchange_failed' }).textContent).toContain(
      "didn't complete the sign-in",
    );

    const unknown = render({ error: '<b>injected</b>' });
    expect(unknown.textContent).toContain('Connecting to Strava failed. Please try again.');
    expect(unknown.textContent).not.toContain('injected');
  });

  it('R3 - connect page shows reauth message', () => {
    expect(render({ reason: 'reauth' }).textContent).toContain(
      'Your Strava connection expired. Please connect again.',
    );
  });

  it('002 R2 - connect page button keeps login href and message uses notice style', () => {
    const page = render({ error: 'access_denied' });
    expect(page.querySelector('.card a.connect')?.getAttribute('href')).toBe('/auth/strava/login');
    const message = page.querySelector('[role="alert"]');
    expect(message?.classList).toContain('notice');
    expect(message?.textContent).toContain('You declined access on Strava');
  });
});
