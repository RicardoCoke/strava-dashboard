import {
  ChangeDetectionStrategy,
  Component,
  ElementRef,
  OnInit,
  computed,
  inject,
  signal,
  viewChild,
} from '@angular/core';
import {
  WeeklySummariesResponse,
  isWeeklySummariesResponse,
} from '../../core/api/weekly-summary.models';
import { WeeklySummaryService } from '../../core/api/weekly-summary.service';
import { PersonStore } from '../../core/person/person.store';
import { sectionLoader } from '../../core/sync/section-loader';
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
  private readonly bell = viewChild.required<ElementRef<HTMLButtonElement>>('bell');

  protected readonly state = signal<State>({ kind: 'loading' });
  protected readonly open = signal(false);
  private readonly selectedWeek = signal<string | null>(null);
  private readonly person = inject(PersonStore).person();
  private readonly seen = signal(readSeenRevision(this.person));

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

  private readonly load = sectionLoader<State>(this.state, {
    fetch: () => this.service.getSummaries(),
    loaded: (data) => (isWeeklySummariesResponse(data) ? { kind: 'loaded', data } : null),
    // Already looking at it: a changed summary must not light the badge (AC11.5).
    afterLoad: () => {
      if (this.open()) {
        this.markSeen();
      }
    },
  });

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
      writeSeenRevision(revision, this.person);
      this.seen.set(revision);
    }
  }
}
