import { TestBed } from '@angular/core/testing';
import { PersonStore } from './person.store';

describe('PersonStore', () => {
  beforeEach(() => localStorage.clear());
  afterEach(() => {
    vi.restoreAllMocks();
    localStorage.clear();
  });

  it('R16 - selection survives reload', () => {
    TestBed.inject(PersonStore).select('marina');
    TestBed.resetTestingModule();
    expect(TestBed.inject(PersonStore).person()).toBe('marina');
  });

  it('R16 - bad or missing value is ricardo', () => {
    expect(TestBed.inject(PersonStore).person()).toBe('ricardo');
    localStorage.setItem('sd.person', 'nobody');
    TestBed.resetTestingModule();
    expect(TestBed.inject(PersonStore).person()).toBe('ricardo');
  });

  it('R16 - switch still works with storage disabled', () => {
    vi.spyOn(Storage.prototype, 'getItem').mockImplementation(() => {
      throw new Error('disabled');
    });
    vi.spyOn(Storage.prototype, 'setItem').mockImplementation(() => {
      throw new Error('disabled');
    });
    const store = TestBed.inject(PersonStore);
    expect(store.person()).toBe('ricardo');
    store.select('marina');
    expect(store.person()).toBe('marina');
  });

  it('R14 - a focus request is taken once, and only after a flip', () => {
    const store = TestBed.inject(PersonStore);
    expect(store.takeFocusRequest()).toBe(false);
    store.select('marina');
    expect(store.takeFocusRequest()).toBe(true);
    expect(store.takeFocusRequest()).toBe(false);
  });
});
