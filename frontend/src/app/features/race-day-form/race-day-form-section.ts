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
import { isRaceDayFormResponse } from '../../core/api/race-day-form.models';
import { RaceDayFormService } from '../../core/api/race-day-form.service';
import { SyncStore } from '../../core/sync/sync.store';
import { RaceDayFormChart } from './race-day-form-chart/race-day-form-chart';
import {
  BAND_CAPTION,
  NOT_ENOUGH_RUNS_TEXT,
  RaceDayFormState,
  notConfiguredText,
  raceDayFormStateFromError,
  subtNoteText,
  tileCaption,
} from './race-day-form.view';

/** The "Race-day form" card under "Training load": its own request, states, tile and chart (feature 011). */
@Component({
  selector: 'app-race-day-form-section',
  imports: [RaceDayFormChart],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './race-day-form-section.html',
  styleUrl: './race-day-form.scss',
})
export class RaceDayFormSection implements OnInit {
  private readonly service = inject(RaceDayFormService);
  private readonly destroyRef = inject(DestroyRef);

  protected readonly notEnoughRunsText = NOT_ENOUGH_RUNS_TEXT;
  protected readonly bandCaption = BAND_CAPTION;
  protected readonly state = signal<RaceDayFormState>({ kind: 'loading' });
  protected readonly data = computed(() => {
    const state = this.state();
    return state.kind === 'loaded' ? state.data : null;
  });
  protected readonly caption = computed(() => {
    const raceDay = this.data()?.raceDay;
    return raceDay ? tileCaption(raceDay) : '';
  });
  protected readonly subtNote = computed(() => {
    const data = this.data();
    return data ? subtNoteText(data) : null;
  });
  protected readonly notConfiguredMessage = computed(() => {
    const state = this.state();
    return state.kind === 'notConfigured' ? notConfiguredText(state.missing) : '';
  });
  protected readonly invalidMessage = computed(() => {
    const state = this.state();
    return state.kind === 'invalidConfig' ? state.message : '';
  });

  constructor() {
    // On the dashboard page, reload in place after each finished sync (009).
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
      .getRaceDayForm()
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (data: unknown) => {
          if (isRaceDayFormResponse(data)) {
            this.state.set({ kind: 'loaded', data });
          } else if (!quiet) {
            this.state.set({ kind: 'error' });
          }
        },
        error: (error: unknown) => {
          const next = raceDayFormStateFromError(error);
          if (!quiet) {
            this.state.set(next);
          }
        },
      });
  }
}
