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
import { isMarathonPaceResponse } from '../../../core/api/marathon-pace.models';
import { MarathonPaceService } from '../../../core/api/marathon-pace.service';
import { SyncStore } from '../../../core/sync/sync.store';
import { ChartComponent } from '../../../shared/chart/chart';
import { MARATHON_PACE_CHART_LABEL, buildMarathonPaceOptions } from '../marathon-pace.options';
import {
  MarathonPaceState,
  isEmpty,
  pendingText,
  stateFromError,
  subtitleText,
  summaryItems,
} from '../marathon-pace.view';

/** The "Marathon pace" card: MP km per build week, with its own request, states and chart. */
@Component({
  selector: 'app-marathon-pace-section',
  imports: [ChartComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './marathon-pace-section.html',
  styleUrl: './marathon-pace-section.scss',
})
export class MarathonPaceSection implements OnInit {
  private readonly service = inject(MarathonPaceService);
  private readonly destroyRef = inject(DestroyRef);

  protected readonly chartLabel = MARATHON_PACE_CHART_LABEL;
  protected readonly state = signal<MarathonPaceState>({ kind: 'loading' });
  protected readonly data = computed(() => {
    const state = this.state();
    return state.kind === 'loaded' ? state.data : null;
  });
  protected readonly empty = computed(() => {
    const data = this.data();
    return data !== null && isEmpty(data);
  });
  protected readonly subtitle = computed(() => {
    const data = this.data();
    return data ? subtitleText(data.goal) : '';
  });
  protected readonly summary = computed(() => {
    const data = this.data();
    return data ? summaryItems(data.summary) : [];
  });
  protected readonly pending = computed(() => pendingText(this.data()?.runsPendingLaps ?? 0));
  protected readonly options = computed(() => {
    const data = this.data();
    return data ? buildMarathonPaceOptions(data) : null;
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
      .getMarathonPace()
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (data: unknown) => {
          if (isMarathonPaceResponse(data)) {
            this.state.set({ kind: 'loaded', data });
          } else if (!quiet) {
            this.state.set({ kind: 'error' });
          }
        },
        error: (error: unknown) => {
          const next = stateFromError(error);
          if (next && !quiet) {
            this.state.set(next);
          }
        },
      });
  }
}
