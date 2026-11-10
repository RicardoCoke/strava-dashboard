import { DestroyRef, WritableSignal, inject } from '@angular/core';
import { takeUntilDestroyed, toObservable } from '@angular/core/rxjs-interop';
import { Observable, Subscription, skip } from 'rxjs';
import { SyncStore } from './sync.store';

interface Loading {
  kind: 'loading';
}
interface Failed {
  kind: 'error';
}

export interface SectionSource<S> {
  fetch: () => Observable<unknown>;
  /** The loaded state for a valid body, or null when the body is not the expected shape. */
  loaded: (body: unknown) => S | null;
  /** The state for a failed request; the plain error state when left out. */
  failed?: (error: unknown) => S;
  afterLoad?: () => void;
}

/**
 * The load cycle every dashboard card shares; call it in an injection context. It returns the card's load function,
 * which shows the spinner and then the answer. On a page with a [SyncStore], a card that has loaded once also reloads
 * quietly after each finished sync (feature 009, R9): what is on screen stays while it runs and if it fails.
 *
 * A newer load cancels one still in flight, so an older answer can't overwrite a newer one. A reload that replaces a
 * load whose spinner is showing is not quiet, so the spinner always ends.
 */
export function sectionLoader<S extends { kind: string }>(
  state: WritableSignal<S | Loading | Failed>,
  source: SectionSource<S>,
): () => void {
  const destroyRef = inject(DestroyRef);
  let request: Subscription | undefined;

  const load = (reload: boolean): void => {
    const quiet = reload && state().kind !== 'loading';
    if (!quiet) {
      state.set({ kind: 'loading' });
    }
    request?.unsubscribe();
    request = source
      .fetch()
      .pipe(takeUntilDestroyed(destroyRef))
      .subscribe({
        next: (body) => {
          const next = source.loaded(body);
          if (next) {
            state.set(next);
            source.afterLoad?.();
          } else if (!quiet) {
            state.set({ kind: 'error' });
          }
        },
        error: (error: unknown) => {
          if (!quiet) {
            state.set(source.failed?.(error) ?? { kind: 'error' });
          }
        },
      });
  };

  const sync = inject(SyncStore, { optional: true });
  if (sync) {
    toObservable(sync.dataVersion)
      .pipe(skip(1), takeUntilDestroyed())
      .subscribe(() => {
        if (request) {
          load(true);
        }
      });
  }
  return () => load(false);
}
