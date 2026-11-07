import { Injectable, signal } from '@angular/core';

export type Person = 'ricardo' | 'marina';

const KEY = 'sd.person';

function readStored(): Person {
  try {
    return localStorage.getItem(KEY) === 'marina' ? 'marina' : 'ricardo';
  } catch {
    return 'ricardo';
  }
}

/** Whose dashboard this tab shows (feature 019). The signal is the live value; storage only survives a reload. */
@Injectable({ providedIn: 'root' })
export class PersonStore {
  private readonly selected = signal<Person>(readStored());
  private focusRequested = false;

  readonly person = this.selected.asReadonly();

  select(person: Person): void {
    this.selected.set(person);
    this.focusRequested = true;
    try {
      localStorage.setItem(KEY, person);
    } catch {
      // Storage disabled: the choice lasts until the page reloads.
    }
  }

  /** True once after a flip: the page is recreated by it, so the new switch has to take the focus back. */
  takeFocusRequest(): boolean {
    const requested = this.focusRequested;
    this.focusRequested = false;
    return requested;
  }
}
