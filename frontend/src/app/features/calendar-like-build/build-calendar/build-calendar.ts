import {
  ChangeDetectionStrategy,
  Component,
  ElementRef,
  afterNextRender,
  computed,
  input,
  viewChild,
} from '@angular/core';
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

  protected readonly rows = computed(() => weekRows(this.weeks()));
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
