import { Component } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { MarathonPaceSection } from '../features/marathon-pace/marathon-pace-section/marathon-pace-section';
import { RunningDashboardPage } from '../features/running-dashboard/running-dashboard-page';

@Component({ selector: 'app-marathon-pace-section', template: '' })
class MarathonPaceSectionStub {}

/** Swaps the marathon pace card for an inert element, so page specs don't see its request. */
export function stubMarathonPaceSection(): void {
  TestBed.overrideComponent(RunningDashboardPage, {
    remove: { imports: [MarathonPaceSection] },
    add: { imports: [MarathonPaceSectionStub] },
  });
}
