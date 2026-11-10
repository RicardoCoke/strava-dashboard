import {
  ChangeDetectionStrategy,
  Component,
  OnInit,
  computed,
  inject,
  signal,
} from '@angular/core';
import { isMarathonPaceResponse } from '../../../core/api/marathon-pace.models';
import { MarathonPaceService } from '../../../core/api/marathon-pace.service';
import { PersonStore } from '../../../core/person/person.store';
import { sectionLoader } from '../../../core/sync/section-loader';
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

  private readonly person = inject(PersonStore).person;

  protected readonly goalPaceVariable = computed(() =>
    this.person() === 'marina' ? 'MARINA_MARATHON_GOAL_PACE' : 'MARATHON_GOAL_PACE',
  );
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

  private readonly load = sectionLoader<MarathonPaceState>(this.state, {
    fetch: () => this.service.getMarathonPace(),
    loaded: (data) => (isMarathonPaceResponse(data) ? { kind: 'loaded', data } : null),
    failed: stateFromError,
  });

  ngOnInit(): void {
    this.load();
  }

  protected retry(): void {
    this.load();
  }
}
