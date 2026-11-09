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
import { isNormalizedVo2maxResponse } from '../../core/api/normalized-vo2max.models';
import { isVo2maxResponse } from '../../core/api/vo2max.models';
import { Vo2maxService } from '../../core/api/vo2max.service';
import { SyncStore } from '../../core/sync/sync.store';
import { formatOneDecimal } from '../../shared/format/numbers';
import { NormalizedVo2maxPanel } from './normalized-vo2max-panel/normalized-vo2max-panel';
import { RacePredictionTiles } from './race-prediction-tiles/race-prediction-tiles';
import { Vo2maxChart } from './vo2max-chart/vo2max-chart';
import {
  NormalizedVo2maxState,
  Vo2maxState,
  basisCaption,
  emptyKind,
  footnoteText,
  normalizedStateFromError,
  runs,
  vo2maxStateFromError,
} from './vo2max.view';

export type Vo2maxTab = 'vo2' | 'normalized';

const TABS: Vo2maxTab[] = ['vo2', 'normalized'];
const step = (tab: Vo2maxTab, by: number): Vo2maxTab =>
  TABS[(TABS.indexOf(tab) + by + TABS.length) % TABS.length];
const TAB_KEYS: Record<string, ((tab: Vo2maxTab) => Vo2maxTab) | undefined> = {
  ArrowRight: (tab) => step(tab, 1),
  ArrowLeft: (tab) => step(tab, -1),
  Home: () => TABS[0],
  End: () => TABS[TABS.length - 1],
};

/**
 * The "VO2max" card under "Training load", with two tabs: "Vo2" (feature 008) and "Normalized Vo2" (feature 016).
 * Each tab has its own request and states; the normalized data loads the first time its tab opens.
 */
@Component({
  selector: 'app-vo2max-section',
  imports: [NormalizedVo2maxPanel, RacePredictionTiles, Vo2maxChart],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './vo2max-section.html',
  styleUrl: './vo2max.scss',
})
export class Vo2maxSection implements OnInit {
  private readonly service = inject(Vo2maxService);
  private readonly destroyRef = inject(DestroyRef);

  protected readonly activeTab = signal<Vo2maxTab>('vo2');
  protected readonly state = signal<Vo2maxState>({ kind: 'loading' });
  protected readonly normalizedState = signal<NormalizedVo2maxState>({ kind: 'idle' });
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
        .subscribe(() => {
          this.load({ quiet: true });
          if (this.normalizedState().kind !== 'idle') {
            this.loadNormalized({ quiet: true });
          }
        });
    }
  }

  ngOnInit(): void {
    this.load();
  }

  protected retry(): void {
    this.load();
  }

  protected selectTab(tab: Vo2maxTab): void {
    this.activeTab.set(tab);
    if (tab === 'normalized' && this.normalizedState().kind === 'idle') {
      this.loadNormalized();
    }
  }

  /** Arrow keys and Home/End move between the tabs and select them (WAI-ARIA tabs pattern). */
  protected onTabKeydown(event: KeyboardEvent): void {
    const next = TAB_KEYS[event.key]?.(this.activeTab());
    if (!next) {
      return;
    }
    event.preventDefault();
    this.selectTab(next);
    const tablist = (event.currentTarget as HTMLElement).closest('[role="tablist"]');
    tablist?.querySelector<HTMLElement>(`#vo2max-tab-${next}`)?.focus();
  }

  protected retryNormalized(): void {
    this.loadNormalized();
  }

  private loadNormalized({ quiet } = { quiet: false }): void {
    if (!quiet) {
      this.normalizedState.set({ kind: 'loading' });
    }
    this.service
      .getNormalizedVo2max()
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (data: unknown) => {
          if (isNormalizedVo2maxResponse(data)) {
            this.normalizedState.set({ kind: 'loaded', data });
          } else if (!quiet) {
            this.normalizedState.set({ kind: 'error' });
          }
        },
        error: (error: unknown) => {
          const next = normalizedStateFromError(error);
          if (!quiet) {
            this.normalizedState.set(next);
          }
        },
      });
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
          if (!quiet) {
            this.state.set(next);
          }
        },
      });
  }
}
