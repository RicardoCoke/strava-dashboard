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
import { isEfficiencyFactorResponse } from '../../core/api/efficiency-factor.models';
import { EfficiencyFactorService } from '../../core/api/efficiency-factor.service';
import { SyncStore } from '../../core/sync/sync.store';
import { formatTwoDecimals } from '../../shared/format/numbers';
import { RingSpinner } from '../../shared/spinner/ring-spinner';
import { EfficiencyFactorChart } from './efficiency-factor-chart/efficiency-factor-chart';
import {
  EfficiencyFactorState,
  NO_CHANGE_NOTE,
  NO_CURRENT_NOTE,
  changeText,
  changeTone,
  currentCaption,
  efStateFromError,
  emptyText,
  footnoteText,
} from './efficiency-factor.view';

/** The "Efficiency factor" card on the running dashboard: its own request, states, tile and chart. */
@Component({
  selector: 'app-efficiency-factor-section',
  imports: [EfficiencyFactorChart, RingSpinner],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './efficiency-factor-section.html',
  styleUrl: './efficiency-factor-section.scss',
})
export class EfficiencyFactorSection implements OnInit {
  private readonly service = inject(EfficiencyFactorService);
  private readonly destroyRef = inject(DestroyRef);
  private request?: Subscription;

  protected readonly noCurrentNote = NO_CURRENT_NOTE;
  protected readonly noChangeNote = NO_CHANGE_NOTE;
  protected readonly state = signal<EfficiencyFactorState>({ kind: 'loading' });
  protected readonly data = computed(() => {
    const state = this.state();
    return state.kind === 'loaded' ? state.data : null;
  });
  protected readonly empty = computed(() => {
    const data = this.data();
    return data ? emptyText(data) : null;
  });
  protected readonly current = computed(() => {
    const current = this.data()?.current;
    return current
      ? { value: formatTwoDecimals(current.efficiency), caption: currentCaption(current) }
      : null;
  });
  protected readonly change = computed(() => {
    const change = this.data()?.change;
    return change ? { text: changeText(change), tone: changeTone(change) } : null;
  });
  protected readonly footnote = computed(() => {
    const data = this.data();
    return data ? footnoteText(data) : [];
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
      .getEfficiencyFactor()
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (data: unknown) => {
          if (isEfficiencyFactorResponse(data)) {
            this.state.set({ kind: 'loaded', data });
          } else if (!quiet) {
            this.state.set({ kind: 'error' });
          }
        },
        error: (error: unknown) => {
          const next = efStateFromError(error);
          if (next && !quiet) {
            this.state.set(next);
          }
        },
      });
  }
}
