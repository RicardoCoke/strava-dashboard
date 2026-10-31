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
import { isVo2maxResponse } from '../../core/api/vo2max.models';
import { Vo2maxService } from '../../core/api/vo2max.service';
import { SyncStore } from '../../core/sync/sync.store';
import { formatOneDecimal } from '../../shared/format/numbers';
import { RacePredictionTiles } from './race-prediction-tiles/race-prediction-tiles';
import { Vo2maxChart } from './vo2max-chart/vo2max-chart';
import {
  Vo2maxState,
  basisCaption,
  emptyKind,
  footnoteText,
  runs,
  vo2maxStateFromError,
} from './vo2max.view';

/** The "VO2max" card under "Training load": its own request, states, tiles and chart. */
@Component({
  selector: 'app-vo2max-section',
  imports: [RacePredictionTiles, Vo2maxChart],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './vo2max-section.html',
  styleUrl: './vo2max.scss',
})
export class Vo2maxSection implements OnInit {
  private readonly service = inject(Vo2maxService);
  private readonly destroyRef = inject(DestroyRef);

  protected readonly state = signal<Vo2maxState>({ kind: 'loading' });
  protected readonly data = computed(() => {
    const state = this.state();
    return state.kind === 'loaded' ? state.data : null;
  });
  protected readonly empty = computed(() => {
    const data = this.data();
    return data ? emptyKind(data) : null;
  });
  protected readonly current = computed(() => {
    const current = this.data()?.current;
    return current
      ? { value: formatOneDecimal(current.vo2max), caption: basisCaption(current) }
      : null;
  });
  protected readonly footnote = computed(() => {
    const data = this.data();
    return data ? footnoteText(data) : [];
  });
  protected readonly pendingText = computed(() => {
    const data = this.data();
    return data
      ? `Lap heart-rate data is still importing: ${runs(data.runsPendingDetail)} to go.`
      : '';
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
      .getVo2max()
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (data: unknown) => {
          if (isVo2maxResponse(data)) {
            this.state.set({ kind: 'loaded', data });
          } else if (!quiet) {
            this.state.set({ kind: 'error' });
          }
        },
        error: (error: unknown) => {
          const next = vo2maxStateFromError(error);
          if (next && !quiet) {
            this.state.set(next);
          }
        },
      });
  }
}
