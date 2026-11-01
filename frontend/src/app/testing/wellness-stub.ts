import { Component } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { RunningDashboardPage } from '../features/running-dashboard/running-dashboard-page';
import { WellnessSection } from '../features/wellness/wellness-section';

@Component({ selector: 'app-wellness-section', template: '' })
class WellnessSectionStub {}

/** Swaps the resting HR & HRV card for an inert element, so page specs don't see its request. */
export function stubWellnessSection(): void {
  TestBed.overrideComponent(RunningDashboardPage, {
    remove: { imports: [WellnessSection] },
    add: { imports: [WellnessSectionStub] },
  });
}
