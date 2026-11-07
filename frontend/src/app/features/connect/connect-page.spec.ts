import { TestBed } from '@angular/core/testing';
import { Router } from '@angular/router';
import { PersonStore } from '../../core/person/person.store';
import { ConnectPage } from './connect-page';

describe('ConnectPage', () => {
  function render(inputs: { error?: string; reason?: string } = {}): HTMLElement {
    const fixture = TestBed.createComponent(ConnectPage);
    Object.entries(inputs).forEach(([key, value]) => fixture.componentRef.setInput(key, value));
    fixture.detectChanges();
    return fixture.nativeElement as HTMLElement;
  }

  beforeEach(() => localStorage.clear());
  afterEach(() => localStorage.clear());

  it('019 R17 - marina link and intro', () => {
    TestBed.inject(PersonStore).select('marina');
    const page = render();
    expect(page.querySelector('a.connect')?.getAttribute('href')).toBe(
      '/auth/strava/login?person=marina',
    );
    expect(page.querySelector('.intro')?.textContent?.trim()).toBe(
      "Connect Marina's Strava account. Sign in to Strava as Marina first.",
    );
  });

  it('019 R17 - ricardo page unchanged', () => {
    const page = render();
    expect(page.querySelector('.intro')?.textContent?.replace(/\s+/g, ' ').trim()).toBe(
      'Connect your Strava account to see your weekly distance, heart-rate trend and workout pace.',
    );
  });

  it('019 R17 - switch on connect page goes home', () => {
    const navigate = vi.spyOn(TestBed.inject(Router), 'navigateByUrl').mockResolvedValue(true);
    render().querySelector<HTMLButtonElement>('app-person-switch button')?.click();
    expect(TestBed.inject(PersonStore).person()).toBe('marina');
    expect(navigate).toHaveBeenCalledWith('/');
  });

  it('019 R17 - wrong athlete, not configured and unknown person messages', () => {
    expect(render({ error: 'wrong_athlete' }).textContent).toContain(
      "That Strava account doesn't belong to this person. Sign out of Strava, sign in to the right account, then try again.",
    );
    expect(render({ error: 'person_not_configured' }).textContent).toContain(
      "Marina's Strava app isn't set up yet. Add the MARINA_ settings to .env, then restart the backend.",
    );
    expect(render({ error: 'unknown_person' }).textContent).toContain(
      'Connecting to Strava failed. Please try again.',
    );
  });

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
