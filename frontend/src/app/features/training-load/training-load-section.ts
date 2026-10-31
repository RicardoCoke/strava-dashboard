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
import { isTrainingLoadResponse } from '../../core/api/training-load.models';
import { TrainingLoadService } from '../../core/api/training-load.service';
import { SyncStore } from '../../core/sync/sync.store';
import { formatShortDate } from '../../shared/format/dates';
import { formatOneDecimal, formatSignedOneDecimal } from '../../shared/format/numbers';
import { RampWarning } from './ramp-warning/ramp-warning';
import { TrainingLoadChart } from './training-load-chart/training-load-chart';
import {
  LoadState,
  emptyText,
  footnoteText,
  isEmpty,
  loadStateFromError,
} from './training-load.view';

/** The "Training load" card under "Marathon build": its own request, states and chart. */
@Component({
  selector: 'app-training-load-section',
  imports: [RampWarning, TrainingLoadChart],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './training-load-section.html',
  styleUrl: './training-load.scss',
})
export class TrainingLoadSection implements OnInit {
  private readonly service = inject(TrainingLoadService);
  private readonly destroyRef = inject(DestroyRef);

  protected readonly state = signal<LoadState>({ kind: 'loading' });
  protected readonly data = computed(() => {
    const state = this.state();
    return state.kind === 'loaded' ? state.data : null;
  });
  protected readonly empty = computed(() => {
    const data = this.data();
    return data !== null && isEmpty(data);
  });
  protected readonly emptyLines = computed(() => {
    const data = this.data();
    return data ? emptyText(data) : [];
  });
  protected readonly footnote = computed(() => {
    const data = this.data();
    return data ? footnoteText(data) : [];
  });
  protected readonly latest = computed(() => {
    const latest = this.data()?.latest;
    return latest
      ? {
          values: [
            { label: 'Fitness', value: formatOneDecimal(latest.fitness) },
            { label: 'Fatigue', value: formatOneDecimal(latest.fatigue) },
            { label: 'Form', value: formatSignedOneDecimal(latest.form) },
          ],
          caption: `on ${formatShortDate(latest.date)}`,
        }
      : null;
  });
  protected readonly invalidMessage = computed(() => {
    const state = this.state();
    return state.kind === 'invalidConfig' ? state.message : '';
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
    this.service
      .getTrainingLoad()
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (data: unknown) => {
          if (isTrainingLoadResponse(data)) {
            this.state.set({ kind: 'loaded', data });
          } else if (!quiet) {
            this.state.set({ kind: 'error' });
          }
        },
        error: (error: unknown) => {
          const next = loadStateFromError(error);
          if (next && !quiet) {
            this.state.set(next);
          }
        },
      });
  }
}
