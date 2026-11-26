import {
  ChangeDetectionStrategy,
  Component,
  OnInit,
  computed,
  inject,
  signal,
} from '@angular/core';
import { takeUntilDestroyed, toObservable } from '@angular/core/rxjs-interop';
import { EMPTY, Subject, catchError, map, of, skip, switchMap } from 'rxjs';
import {
  CompareFilter,
  CompareRunItem,
  WorkoutCompareResponse,
  isCompareRunsResponse,
  isWorkoutCompareResponse,
} from '../../core/api/workout-compare.models';
import { WorkoutCompareService } from '../../core/api/workout-compare.service';
import { SyncStore } from '../../core/sync/sync.store';
import { ChartComponent } from '../../shared/chart/chart';
import { NavMenu } from '../../shared/nav-menu/nav-menu';
import { PersonSwitch } from '../../shared/person-switch/person-switch';
import { RingSpinner } from '../../shared/spinner/ring-spinner';
import { WeeklySummaryBell } from '../weekly-summary/weekly-summary-bell';
import {
  CompareAxis,
  CompareZoom,
  ShownRuns,
  buildCompareOptions,
  compareChartLabel,
} from './workout-compare.options';
import {
  DRIFT_FOOTNOTE,
  PIECES_CAVEAT,
  coarseNote,
  emptyListText,
  hasNoPoints,
  listRowText,
  missingPointsNote,
  promptText,
  repHeaders,
  repRows,
  repsNote,
  showDriftFootnote,
  statsHeaders,
  statsRows,
} from './workout-compare.view';

type ListState =
  { kind: 'loading' } | { kind: 'error' } | { kind: 'loaded'; runs: CompareRunItem[] };

type CompareState =
  | { kind: 'idle' }
  | { kind: 'loading' }
  | { kind: 'error' }
  | { kind: 'loaded'; data: WorkoutCompareResponse };

const FILTERS: { value: CompareFilter; label: string }[] = [
  { value: 'all', label: 'All' },
  { value: 'workouts', label: 'Workouts' },
  { value: 'long', label: 'Long' },
  { value: 'easy', label: 'Easy' },
];

const MAX_SELECTED = 2;
const ZOOMS: CompareZoom[] = [1, 2, 4];

/** Two of the person's runs side by side: one pace and HR chart, stats and reps (feature 025). */
@Component({
  selector: 'app-workout-compare-page',
  imports: [ChartComponent, NavMenu, PersonSwitch, RingSpinner, WeeklySummaryBell],
  changeDetection: ChangeDetectionStrategy.OnPush,
  providers: [SyncStore],
  templateUrl: './workout-compare-page.html',
  styleUrl: './workout-compare-page.scss',
})
export class WorkoutComparePage implements OnInit {
  private readonly service = inject(WorkoutCompareService);
  private readonly sync = inject(SyncStore);
  private readonly listLoads = new Subject<CompareFilter>();
  private readonly compareLoads = new Subject<[number, number] | null>();

  protected readonly filters = FILTERS;
  protected readonly piecesCaveat = PIECES_CAVEAT;
  protected readonly driftFootnote = DRIFT_FOOTNOTE;
  protected readonly filter = signal<CompareFilter>('all');
  protected readonly list = signal<ListState>({ kind: 'loading' });
  protected readonly selected = signal<number[]>([]);
  protected readonly compare = signal<CompareState>({ kind: 'idle' });
  protected readonly axis = signal<CompareAxis>('distance');
  protected readonly zooms = ZOOMS;
  protected readonly zoom = signal<CompareZoom>(1);
  // Not a signal: ECharts hides the run itself, so a legend click needs no rebuild (which would also reset the
  // scroll bar). The tooltip reads it on each hover and the next rebuild starts from it.
  private shown: ShownRuns = {};

  protected readonly emptyText = computed(() => emptyListText(this.filter()));
  protected readonly prompt = computed(() => promptText(this.selected().length));
  protected readonly rows = computed(() => {
    const list = this.list();
    const selected = this.selected();
    return list.kind !== 'loaded'
      ? []
      : list.runs.map((run) => ({
          id: run.id,
          text: listRowText(run),
          name: run.name,
          treadmill: run.treadmill,
          checked: selected.includes(run.id),
        }));
  });
  protected readonly view = computed(() => {
    const state = this.compare();
    if (state.kind !== 'loaded') {
      return null;
    }
    const data = state.data;
    return {
      options: buildCompareOptions(data, this.axis(), this.shown, this.zoom()),
      label: compareChartLabel(this.axis()),
      coarse: coarseNote(data),
      noPoints: hasNoPoints(data),
      missing: missingPointsNote(data),
      statsHeaders: statsHeaders(data),
      stats: statsRows(data),
      driftFootnote: showDriftFootnote(data),
      reps: data.reps,
      repHeaders: repHeaders(data),
      repRows: repRows(data),
      repsNote: repsNote(data),
    };
  });

  constructor() {
    // switchMap cancels the older request, so only the latest filter's or pair's answer is shown.
    this.listLoads
      .pipe(
        switchMap((filter) =>
          this.service.getRuns(filter).pipe(
            map((data) => (isCompareRunsResponse(data) ? data.runs : null)),
            catchError(() => of(null)),
          ),
        ),
        takeUntilDestroyed(),
      )
      .subscribe((runs) => {
        if (runs) {
          this.list.set({ kind: 'loaded', runs });
        } else if (this.list().kind !== 'loaded') {
          // A failed quiet reload keeps the list; one that replaced a visible load still ends the spinner.
          this.list.set({ kind: 'error' });
        }
      });
    this.compareLoads
      .pipe(
        switchMap((pair) =>
          pair === null
            ? EMPTY
            : this.service.compare(...pair).pipe(
                map((data) => (isWorkoutCompareResponse(data) ? data : null)),
                catchError(() => of(null)),
              ),
        ),
        takeUntilDestroyed(),
      )
      .subscribe((data) => this.compare.set(data ? { kind: 'loaded', data } : { kind: 'error' }));
    // After each finished sync, reload the list in place (feature 009, R9). The first value is the initial 0.
    toObservable(this.sync.dataVersion)
      .pipe(skip(1), takeUntilDestroyed())
      .subscribe(() => this.listLoads.next(this.filter()));
  }

  ngOnInit(): void {
    this.loadList();
    this.sync.start();
  }

  protected selectFilter(filter: CompareFilter): void {
    this.filter.set(filter);
    this.select([]);
    this.loadList();
  }

  protected loadList(): void {
    this.list.set({ kind: 'loading' });
    this.listLoads.next(this.filter());
  }

  /** A third pick replaces the one picked first. */
  protected toggle(id: number, checked: boolean): void {
    const without = this.selected().filter((selected) => selected !== id);
    this.select(checked ? [...without, id].slice(-MAX_SELECTED) : without);
  }

  protected loadComparison(): void {
    const [a, b] = this.selected();
    this.compare.set({ kind: 'loading' });
    this.shown = {}; // a new pair starts with both runs shown
    this.compareLoads.next([a, b]);
  }

  /** Kept in place: the tooltip already built holds this object. */
  protected onLegend(selected: ShownRuns): void {
    Object.assign(this.shown, selected);
  }

  private select(ids: number[]): void {
    this.selected.set(ids);
    if (ids.length === MAX_SELECTED) {
      this.loadComparison();
    } else {
      this.compare.set({ kind: 'idle' });
      this.compareLoads.next(null);
    }
  }
}
