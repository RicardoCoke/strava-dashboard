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
import { Subscription, skip } from 'rxjs';
import { isLongRunDriftResponse } from '../../core/api/long-run-drift.models';
import { LongRunDriftService } from '../../core/api/long-run-drift.service';
import { SyncStore } from '../../core/sync/sync.store';
import { LongRunDriftChart } from './long-run-drift-chart/long-run-drift-chart';
import {
  LongRunDriftState,
  driftStateFromError,
  emptyKind,
  excludedText,
  headerTexts,
  legendText,
  pendingText,
} from './long-run-drift.view';

/** The "Long-run HR drift" card under "Heart rate by run type": its own request, states and chart. */
@Component({
  selector: 'app-long-run-drift-section',
  imports: [LongRunDriftChart],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './long-run-drift-section.html',
  styleUrl: './long-run-drift-section.scss',
})
export class LongRunDriftSection implements OnInit {
  private readonly service = inject(LongRunDriftService);
  private readonly destroyRef = inject(DestroyRef);
  private request?: Subscription;

  protected readonly state = signal<LongRunDriftState>({ kind: 'loading' });
  protected readonly data = computed(() => {
    const state = this.state();
    return state.kind === 'loaded' ? state.data : null;
  });
  protected readonly empty = computed(() => {
    const data = this.data();
    return data ? emptyKind(data) : null;
  });
  protected readonly header = computed(() => {
    const data = this.data();
    return data ? headerTexts(data) : [];
  });
  protected readonly excluded = computed(() => {
    const data = this.data();
    return data ? excludedText(data.excluded) : null;
  });
  protected readonly pending = computed(() => {
    const data = this.data();
    return data ? pendingText(data.pendingCount) : null;
  });
  protected readonly legend = computed(() => {
    const data = this.data();
    return data ? legendText(data.thresholds) : '';
  });

  constructor() {
    // On the dashboard page, reload in place after each finished sync (feature 009, R9).
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

  /** A quiet reload keeps the current content while it runs and if it fails. */
  private load({ quiet } = { quiet: false }): void {
    if (!quiet) {
      this.state.set({ kind: 'loading' });
    }
    // A newer load replaces one still in flight, so an older response can't overwrite a newer one.
    this.request?.unsubscribe();
    this.request = this.service
      .getLongRunDrift()
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (data: unknown) => {
          if (isLongRunDriftResponse(data)) {
            this.state.set({ kind: 'loaded', data });
          } else if (!quiet) {
            this.state.set({ kind: 'error' });
          }
        },
        error: (error: unknown) => {
          const next = driftStateFromError(error);
          if (next && !quiet) {
            this.state.set(next);
          }
        },
      });
  }
}
