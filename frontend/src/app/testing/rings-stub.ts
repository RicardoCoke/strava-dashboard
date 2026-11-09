import { Component } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { RunningDashboardPage } from '../features/running-dashboard/running-dashboard-page';
import { RingsSection } from '../features/rings/rings-section';

@Component({ selector: 'app-rings-section', template: '' })
class RingsSectionStub {}

/** Swaps the rings for an inert element, so page specs don't see their request. */
export function stubRingsSection(): void {
  TestBed.overrideComponent(RunningDashboardPage, {
    remove: { imports: [RingsSection] },
    add: { imports: [RingsSectionStub] },
  });
}
