import { HttpErrorResponse } from '@angular/common/http';
import {
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  ElementRef,
  OnInit,
  computed,
  inject,
  signal,
  viewChild,
} from '@angular/core';
import { takeUntilDestroyed, toObservable } from '@angular/core/rxjs-interop';
import { Subscription, skip } from 'rxjs';
import {
  WeeklySummariesResponse,
  isWeeklySummariesResponse,
} from '../../core/api/weekly-summary.models';
import { WeeklySummaryService } from '../../core/api/weekly-summary.service';
import { SyncStore } from '../../core/sync/sync.store';
import { lastWeekMonday, readSeenRevision, writeSeenRevision } from './seen-revision';
import { PanelState, WeeklySummaryPanel } from './weekly-summary-panel/weekly-summary-panel';

type State =
  { kind: 'loading' } | { kind: 'error' } | { kind: 'loaded'; data: WeeklySummariesResponse };

/** The bell in the dashboard header: owns the weekly summaries request, the badge and the panel (feature 017). */
@Component({
  selector: 'app-weekly-summary-bell',
  imports: [WeeklySummaryPanel],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './weekly-summary-bell.html',
  styleUrl: './weekly-summary-bell.scss',
  host: { '(document:keydown.escape)': 'close()' },
})
export class WeeklySummaryBell implements OnInit {
  private readonly service = inject(WeeklySummaryService);
  private readonly destroyRef = inject(DestroyRef);
  private readonly bell = viewChild.required<ElementRef<HTMLButtonElement>>('bell');
  private request?: Subscription;

  protected readonly state = signal<State>({ kind: 'loading' });
  protected readonly open = signal(false);
  private readonly selectedWeek = signal<string | null>(null);
  private readonly seen = signal(readSeenRevision());

  protected readonly panelState = computed<PanelState>(() => this.state().kind);
  private readonly summaries = computed(() => {
    const state = this.state();
    return state.kind === 'loaded' ? state.data.summaries : [];
  });
  /** The selected week if it is still listed, else the newest (AC13.4). */
  protected readonly index = computed(() =>
    Math.max(
      0,
      this.summaries().findIndex((s) => s.weekStart === this.selectedWeek()),
    ),
  );
  protected readonly summary = computed(() => this.summaries()[this.index()] ?? null);
  protected readonly hasOlder = computed(() => this.index() < this.summaries().length - 1);
  /** Only the current or last week can light the badge, and only until it is opened (R11). */
  protected readonly unseen = computed(() => {
    const newest = this.summaries()[0];
    return (
      newest !== undefined &&
      newest.weekStart >= lastWeekMonday(new Date()) &&
      newest.revision !== this.seen()
    );
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

  protected toggle(): void {
    if (this.open()) {
      this.close();
      return;
    }
    this.selectedWeek.set(null);
    this.open.set(true);
    this.markSeen();
  }

  protected close(): void {
    if (!this.open()) {
      return;
    }
    this.open.set(false);
    this.bell().nativeElement.focus();
  }

  protected older(): void {
    this.selectedWeek.set(this.summaries()[this.index() + 1]?.weekStart ?? null);
  }

  protected newer(): void {
    this.selectedWeek.set(this.summaries()[this.index() - 1]?.weekStart ?? null);
  }

  protected retry(): void {
    this.load();
  }

  private markSeen(): void {
    const revision = this.summaries()[0]?.revision;
    if (revision !== undefined) {
      writeSeenRevision(revision);
      this.seen.set(revision);
    }
  }

  /** A quiet reload keeps the current content while it runs and if it fails. */
  private load(options = { quiet: false }): void {
    const quiet = options.quiet && this.state().kind === 'loaded';
    if (!quiet) {
      this.state.set({ kind: 'loading' });
    }
    this.request?.unsubscribe();
    this.request = this.service
      .getSummaries()
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (data: unknown) => {
          if (isWeeklySummariesResponse(data)) {
            this.state.set({ kind: 'loaded', data });
            // Already looking at it: a changed summary must not light the badge (AC11.5).
            if (this.open()) {
              this.markSeen();
            }
          } else if (!quiet) {
            this.state.set({ kind: 'error' });
          }
        },
        error: (error: unknown) => {
          // A 401 is handled by the interceptor, which navigates to /connect.
          if (!quiet && !(error instanceof HttpErrorResponse && error.status === 401)) {
            this.state.set({ kind: 'error' });
          }
        },
      });
  }
}
