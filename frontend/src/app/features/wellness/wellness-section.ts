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
import { isWellnessResponse } from '../../core/api/wellness.models';
import { WellnessService } from '../../core/api/wellness.service';
import { SyncStore } from '../../core/sync/sync.store';
import { WellnessChart } from './wellness-chart/wellness-chart';
import { WellnessLatest } from './wellness-latest/wellness-latest';
import {
  WellnessState,
  emptyText,
  latestTiles,
  staleText,
  wellnessStateFromError,
} from './wellness.view';

/** The "Resting HR & HRV" card under "Training load" (feature 015): its own request, states, tiles and chart. */
@Component({
  selector: 'app-wellness-section',
  imports: [WellnessChart, WellnessLatest],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './wellness-section.html',
  styleUrl: './wellness-section.scss',
})
export class WellnessSection implements OnInit {
  private readonly service = inject(WellnessService);
  private readonly destroyRef = inject(DestroyRef);

  protected readonly state = signal<WellnessState>({ kind: 'loading' });
  protected readonly data = computed(() => {
    const state = this.state();
    return state.kind === 'loaded' ? state.data : null;
  });
  protected readonly empty = computed(() => this.data()?.daysWithData === 0);
  protected readonly emptyLine = computed(() => {
    const data = this.data();
    return data ? emptyText(data.source) : '';
  });
  protected readonly staleLine = computed(() => {
    const data = this.data();
    return data ? staleText(data.source) : null;
  });
  protected readonly tiles = computed(() => {
    const data = this.data();
    return data ? latestTiles(data) : [];
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
      .getWellness()
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (data: unknown) => {
          if (isWellnessResponse(data)) {
            this.state.set({ kind: 'loaded', data });
          } else if (!quiet) {
            this.state.set({ kind: 'error' });
          }
        },
        error: (error: unknown) => {
          const next = wellnessStateFromError(error);
          if (next && !quiet) {
            this.state.set(next);
          }
        },
      });
  }
}
