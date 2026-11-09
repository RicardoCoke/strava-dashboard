import { HttpErrorResponse } from '@angular/common/http';
import {
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  OnInit,
  computed,
  inject,
  signal,
} from '@angular/core';
import { takeUntilDestroyed, toObservable } from '@angular/core/rxjs-interop';
import { skip } from 'rxjs';
import { isRingsResponse } from '../../core/api/rings.models';
import { RingsService } from '../../core/api/rings.service';
import { SyncStore } from '../../core/sync/sync.store';
import { LOADING_RINGS, RingView, ringViews } from './rings.view';

type RingsState = { kind: 'loading' } | { kind: 'error' } | { kind: 'loaded'; rings: RingView[] };

/** Circumference of the r = 45 ring in the 100 × 100 viewBox. */
const CIRCUMFERENCE = 2 * Math.PI * 45;

/** Whoop-style Sleep, Recovery and Strain rings at the top of the dashboard (feature 020). */
@Component({
  selector: 'app-rings-section',
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './rings-section.html',
  styleUrl: './rings-section.scss',
})
export class RingsSection implements OnInit {
  private readonly service = inject(RingsService);
  private readonly destroyRef = inject(DestroyRef);

  protected readonly circumference = CIRCUMFERENCE;
  protected readonly state = signal<RingsState>({ kind: 'loading' });
  protected readonly rings = computed(() => {
    const state = this.state();
    return state.kind === 'loaded' ? state.rings : LOADING_RINGS;
  });

  constructor() {
    // Reload in place after each finished sync (feature 009, R9).
    const sync = inject(SyncStore, { optional: true });
    if (sync) {
      toObservable(sync.dataVersion)
        .pipe(skip(1), takeUntilDestroyed())
        .subscribe(() => this.load({ quiet: true }));
    }
  }

  ngOnInit(): void {
    this.load();
  }

  protected retry(): void {
    this.load();
  }

  protected dashOffset(fill: number): number {
    return CIRCUMFERENCE * (1 - fill);
  }

  /** A quiet reload keeps the current content while it runs and if it fails. */
  private load({ quiet } = { quiet: false }): void {
    if (!quiet) {
      this.state.set({ kind: 'loading' });
    }
    this.service
      .getRings()
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (data: unknown) => {
          if (isRingsResponse(data)) {
            this.state.set({ kind: 'loaded', rings: ringViews(data) });
          } else if (!quiet) {
            this.state.set({ kind: 'error' });
          }
        },
        error: (error: unknown) => {
          // A 401 is handled by the interceptor.
          const unauthorized = error instanceof HttpErrorResponse && error.status === 401;
          if (!quiet && !unauthorized) {
            this.state.set({ kind: 'error' });
          }
        },
      });
  }
}
