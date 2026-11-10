import {
  ChangeDetectionStrategy,
  Component,
  OnInit,
  computed,
  inject,
  signal,
} from '@angular/core';
import { takeUntilDestroyed, toObservable } from '@angular/core/rxjs-interop';
import { Subject, catchError, map, of, skip, switchMap } from 'rxjs';
import { HistoryRange, RingsHistoryDay, isRingsHistoryResponse } from '../../core/api/rings.models';
import { RingsService } from '../../core/api/rings.service';
import { SyncStore } from '../../core/sync/sync.store';
import { ChartComponent } from '../../shared/chart/chart';
import { NavMenu } from '../../shared/nav-menu/nav-menu';
import { PersonSwitch } from '../../shared/person-switch/person-switch';
import { RingSpinner } from '../../shared/spinner/ring-spinner';
import { WeeklySummaryBell } from '../weekly-summary/weekly-summary-bell';
import {
  HistoryMetric,
  buildRecoveryHistoryOptions,
  buildSleepHistoryOptions,
  buildStrainHistoryOptions,
  historyChartLabel,
  isEmptySeries,
} from './recovery-history.options';

type RecoveryState =
  { kind: 'loading' } | { kind: 'error' } | { kind: 'loaded'; days: RingsHistoryDay[] };

const BUILDERS = {
  sleep: buildSleepHistoryOptions,
  recovery: buildRecoveryHistoryOptions,
  strain: buildStrainHistoryOptions,
};

const TITLES: Record<HistoryMetric, string> = {
  sleep: 'Sleep',
  recovery: 'Recovery',
  strain: 'Strain',
};

const VALUES: Record<HistoryMetric, (day: RingsHistoryDay) => number | null> = {
  sleep: (day) => day.sleepPercent,
  recovery: (day) => day.recoveryPercent,
  strain: (day) => day.strain,
};

/** Daily history of the three ring scores over 7, 30 or 90 days (feature 023). */
@Component({
  selector: 'app-recovery-page',
  imports: [ChartComponent, NavMenu, PersonSwitch, RingSpinner, WeeklySummaryBell],
  changeDetection: ChangeDetectionStrategy.OnPush,
  providers: [SyncStore],
  templateUrl: './recovery-page.html',
  styleUrl: './recovery-page.scss',
})
export class RecoveryPage implements OnInit {
  private readonly service = inject(RingsService);
  private readonly sync = inject(SyncStore);
  private readonly loads = new Subject<HistoryRange>();

  protected readonly ranges: HistoryRange[] = [7, 30, 90];
  protected readonly range = signal<HistoryRange>(30);
  protected readonly state = signal<RecoveryState>({ kind: 'loading' });
  protected readonly charts = computed(() => {
    const state = this.state();
    if (state.kind !== 'loaded') {
      return [];
    }
    return (['sleep', 'recovery', 'strain'] as const).map((metric) => ({
      metric,
      title: TITLES[metric],
      empty: isEmptySeries(state.days.map(VALUES[metric])),
      options: BUILDERS[metric](state.days),
      label: historyChartLabel(metric, state.days.length),
    }));
  });

  constructor() {
    // switchMap cancels the older request, so only the latest range's answer is shown.
    this.loads
      .pipe(
        switchMap((range) =>
          this.service.getHistory(range).pipe(
            map((data) => (isRingsHistoryResponse(data) ? data : null)),
            catchError(() => of(null)),
          ),
        ),
        takeUntilDestroyed(),
      )
      .subscribe((data) => {
        if (data) {
          this.state.set({ kind: 'loaded', days: data.days });
        } else if (this.state().kind !== 'loaded') {
          // A failed quiet reload keeps the graphs; one that replaced a visible load still ends the spinner.
          this.state.set({ kind: 'error' });
        }
      });
    // After each finished sync, reload in place (feature 009, R9). The first value is the initial 0.
    toObservable(this.sync.dataVersion)
      .pipe(skip(1), takeUntilDestroyed())
      .subscribe(() => this.loads.next(this.range()));
  }

  ngOnInit(): void {
    this.load();
    this.sync.start();
  }

  protected select(range: HistoryRange): void {
    this.range.set(range);
    this.load();
  }

  protected load(): void {
    this.state.set({ kind: 'loading' });
    this.loads.next(this.range());
  }
}
