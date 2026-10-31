import { Component } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { RunningDashboardPage } from '../features/running-dashboard/running-dashboard-page';
import { Vo2maxSection } from '../features/vo2max/vo2max-section';

@Component({ selector: 'app-vo2max-section', template: '' })
class Vo2maxSectionStub {}

/** Swaps the VO2max card for an inert element, so page specs don't see its request. */
export function stubVo2maxSection(): void {
  TestBed.overrideComponent(RunningDashboardPage, {
    remove: { imports: [Vo2maxSection] },
    add: { imports: [Vo2maxSectionStub] },
  });
}
