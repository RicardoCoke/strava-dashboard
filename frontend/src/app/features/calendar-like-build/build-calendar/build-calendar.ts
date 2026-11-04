import {
  ChangeDetectionStrategy,
  Component,
  ElementRef,
  afterNextRender,
  computed,
  input,
  viewChild,
} from '@angular/core';
import { RunWeatherDay } from '../../../core/api/run-weather.models';
import { BuildProgress, PlanWeek } from '../../../core/api/training-plan.models';
import { scrollTargetWeekIndex, weekRows } from '../plan-view';

@Component({
  selector: 'app-build-calendar',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="scroller" #scroller>
      @for (week of rows(); track week.index) {
        <div
          class="week"
          [class.current-week]="week.isCurrentWeek"
          [attr.aria-current]="week.isCurrentWeek ? 'true' : null"
          [attr.data-week-index]="week.index"
        >
          <div class="summary">
            <span class="week-label">{{ week.label }}</span>
            @if (week.isCurrentWeek) {
              <span class="tag">This week</span>
            }
            <span class="week-km">{{ week.kmText }}</span>
            <span class="week-split">{{ week.splitText }}</span>
          </div>
          @for (day of week.days; track day.date) {
            <div
              class="day"
              [class.key-workout]="day.isKeyWorkout"
              [class.rest]="day.isRest"
              [class.race]="day.raceLabel !== null"
              [class.marathon]="day.isMarathon"
              [class.today]="day.isToday"
              [class.past]="day.isPast"
              [attr.aria-current]="day.isToday ? 'date' : null"
            >
              <span class="date">{{ day.dateText }}</span>
              <div class="body">
                @if (day.isToday) {
                  <span class="badge">Today</span>
                }
                @if (day.raceLabel) {
                  <span class="badge race-badge">{{ day.raceLabel }}</span>
                }
                @if (day.isRest) {
                  <span class="session">Rest</span>
                } @else {
                  @if (day.session) {
                    <span class="session">{{ day.session }}</span>
                  }
                  @if (day.note) {
                    <span class="day-note">{{ day.note }}</span>
                  }
                  <span class="numbers">{{ day.minutesText }} · {{ day.kmText }}</span>
                }
                @if (day.weather; as weather) {
                  <span class="weather" [attr.data-condition]="weather.condition">
                    <svg
                      viewBox="0 0 24 24"
                      width="18"
                      height="18"
                      role="img"
                      fill="currentColor"
                      [attr.aria-label]="weather.label"
                    >
                      @switch (weather.condition) {
                        @case ('sun') {
                          <circle cx="12" cy="12" r="5" />
                          <path
                            d="M12 2v3M12 19v3M2 12h3M19 12h3M5 5l2 2M17 17l2 2M5 19l2-2M17 7l2-2"
                            stroke="currentColor"
                            stroke-width="2"
                            stroke-linecap="round"
                          />
                        }
                        @case ('cloud') {
                          <path d="M7 19a4.5 4.5 0 0 1-.7-8.9 6 6 0 0 1 11.5-.9A5 5 0 0 1 17 19z" />
                        }
                        @case ('rain') {
                          <path
                            d="M7 15a4 4 0 0 1-.6-7.95A5.5 5.5 0 0 1 17 6.1 4.5 4.5 0 0 1 17 15z"
                          />
                          <path
                            d="M8 18l-1 3M13 18l-1 3M18 18l-1 3"
                            stroke="currentColor"
                            stroke-width="2"
                            stroke-linecap="round"
                          />
                        }
                      }
                    </svg>
                    @if (weather.rainText) {
                      <span class="rain-mm">{{ weather.rainText }}</span>
                    }
                  </span>
                }
              </div>
            </div>
          }
        </div>
      }
    </div>
  `,
  styleUrl: './build-calendar.scss',
})
export class BuildCalendar {
  readonly weeks = input.required<PlanWeek[]>();
  readonly progress = input.required<BuildProgress>();
  readonly weather = input<RunWeatherDay[]>([]);

  protected readonly rows = computed(() => weekRows(this.weeks(), this.weather()));
  private readonly scroller = viewChild.required<ElementRef<HTMLElement>>('scroller');

  constructor() {
    // Open the calendar on the current week without scrolling the page itself.
    afterNextRender(() => {
      const container = this.scroller().nativeElement;
      const index = scrollTargetWeekIndex(this.progress(), this.weeks());
      const row = container.querySelector<HTMLElement>(`[data-week-index="${index}"]`);
      if (row) {
        container.scrollTop = row.offsetTop;
      }
    });
  }
}
