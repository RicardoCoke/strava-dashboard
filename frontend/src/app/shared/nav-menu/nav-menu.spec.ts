import { Component } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { NavMenu } from './nav-menu';

@Component({ selector: 'app-page', imports: [NavMenu], template: '<app-nav-menu />' })
class Page {}

describe('NavMenu', () => {
  let harness: RouterTestingHarness;

  beforeEach(async () => {
    TestBed.configureTestingModule({
      providers: [
        provideRouter([
          { path: '', pathMatch: 'full', component: Page },
          { path: 'recovery', component: Page },
        ]),
      ],
    });
    harness = await RouterTestingHarness.create('/');
  });

  const el = () => harness.fixture.nativeElement as HTMLElement;
  const button = () => el().querySelector('.menu-button') as HTMLButtonElement;
  const panel = () => el().querySelector('[role="dialog"]');
  const links = () => Array.from(el().querySelectorAll<HTMLAnchorElement>('.panel a'));

  async function openMenu(): Promise<void> {
    button().click();
    harness.detectChanges();
    await harness.fixture.whenStable();
  }

  it('R1 - menu button opens panel with two links', async () => {
    expect(button().getAttribute('aria-label')).toBe('Open menu');
    expect(button().getAttribute('aria-controls')).toBe('nav-menu-panel');
    await openMenu();
    expect(links().map((link) => [link.textContent?.trim(), link.getAttribute('href')])).toEqual([
      ['Performance', '/'],
      ['Recovery', '/recovery'],
    ]);
  });

  it('R1 - panel is a modal dialog and button is expanded', async () => {
    expect(button().getAttribute('aria-expanded')).toBe('false');
    await openMenu();
    expect(panel()?.getAttribute('aria-modal')).toBe('true');
    expect(panel()?.getAttribute('aria-label')).toBe('Menu');
    expect(button().getAttribute('aria-expanded')).toBe('true');
  });

  it('R1 - current page link is marked active', async () => {
    await openMenu();
    expect(links().map((link) => link.getAttribute('aria-current'))).toEqual(['page', null]);
    await harness.navigateByUrl('/recovery');
    await openMenu();
    expect(links().map((link) => link.getAttribute('aria-current'))).toEqual([null, 'page']);
    expect(links()[1].classList).toContain('active');
  });

  it('R2 - picking an entry navigates and closes the panel', async () => {
    await openMenu();
    links()[1].click();
    await harness.fixture.whenStable();
    harness.detectChanges();
    expect(TestBed.inject(Router).url).toBe('/recovery');
    expect(panel()).toBeNull();
  });

  it('R2 - picking the current page closes the panel without navigating', async () => {
    await openMenu();
    links()[0].click();
    await harness.fixture.whenStable();
    harness.detectChanges();
    expect(TestBed.inject(Router).url).toBe('/');
    expect(panel()).toBeNull();
  });

  it('R2 - tab stays inside the open panel', async () => {
    await openMenu();
    const close = el().querySelector('.close') as HTMLButtonElement;
    const tab = (shiftKey: boolean) =>
      panel()?.dispatchEvent(
        new KeyboardEvent('keydown', { key: 'Tab', shiftKey, bubbles: true, cancelable: true }),
      );
    links()[1].focus();
    tab(false);
    expect(document.activeElement).toBe(close);
    tab(true);
    expect(document.activeElement).toBe(links()[1]);
  });

  it('R2 - escape, backdrop and close button close and refocus', async () => {
    const closers = [
      () => document.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape' })),
      () => (el().querySelector('.backdrop') as HTMLElement).click(),
      () => (el().querySelector('.close') as HTMLButtonElement).click(),
    ];
    for (const close of closers) {
      await openMenu();
      close();
      harness.detectChanges();
      expect(panel()).toBeNull();
      expect(document.activeElement).toBe(button());
    }
  });

  it('R2 - focus moves to first link and closed panel is not rendered', async () => {
    expect(el().querySelector('.panel a')).toBeNull();
    await openMenu();
    expect(document.activeElement).toBe(links()[0]);
  });
});
