import { Component } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { BuildCalendarSection } from '../features/calendar-like-build/build-calendar-section/build-calendar-section';
import { CalendarLikeBuild } from '../features/calendar-like-build/calendar-like-build';
import { RunningDashboardPage } from '../features/running-dashboard/running-dashboard-page';

@Component({ selector: 'app-calendar-like-build', template: '' })
class CalendarLikeBuildStub {}

@Component({ selector: 'app-build-calendar-section', template: '' })
class BuildCalendarSectionStub {}

/** Swaps both marathon build cards for inert elements, so page specs see only the dashboard's own request. */
export function stubPlanSection(): void {
  TestBed.overrideComponent(RunningDashboardPage, {
    remove: { imports: [CalendarLikeBuild, BuildCalendarSection] },
    add: { imports: [CalendarLikeBuildStub, BuildCalendarSectionStub] },
  });
}
