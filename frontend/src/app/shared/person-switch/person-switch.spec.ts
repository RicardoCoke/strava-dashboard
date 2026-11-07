import { TestBed } from '@angular/core/testing';
import { PersonStore } from '../../core/person/person.store';
import { PersonSwitch } from './person-switch';

describe('PersonSwitch', () => {
  beforeEach(() => localStorage.clear());
  afterEach(() => localStorage.clear());

  function render(): { el: HTMLElement; control: HTMLButtonElement; detect: () => void } {
    const fixture = TestBed.createComponent(PersonSwitch);
    fixture.detectChanges();
    const el = fixture.nativeElement as HTMLElement;
    return {
      el,
      control: el.querySelector('button') as HTMLButtonElement,
      detect: () => fixture.detectChanges(),
    };
  }

  it('R13 - switch role and label', () => {
    const { el, control } = render();
    // R13: a native button, so Space and Enter already flip it.
    expect(control.tagName).toBe('BUTTON');
    expect(control.getAttribute('role')).toBe('switch');
    expect(control.getAttribute('aria-label')).toBe('Show dashboard for Marina');
    expect(control.getAttribute('aria-checked')).toBe('false');
    const names = Array.from(el.children).map((child) => child.textContent?.trim());
    expect(names).toEqual(['Ricardo', '', 'Marina']);
    expect(el.children[1]).toBe(control);
  });

  it('R13 - flipping selects the other person and back', () => {
    const { control, detect } = render();
    const store = TestBed.inject(PersonStore);

    control.click();
    detect();
    expect(store.person()).toBe('marina');
    expect(control.getAttribute('aria-checked')).toBe('true');

    control.click();
    detect();
    expect(store.person()).toBe('ricardo');
  });

  it('R14 - focus stays on switch', async () => {
    TestBed.inject(PersonStore).select('marina');
    const { control } = render();
    await new Promise((resolve) => setTimeout(resolve));
    expect(document.activeElement).toBe(control);
  });

  it('R14 - a page load does not steal focus', async () => {
    const { control } = render();
    await new Promise((resolve) => setTimeout(resolve));
    expect(document.activeElement).not.toBe(control);
  });
});
