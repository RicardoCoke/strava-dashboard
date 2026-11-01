import { Component } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { RaceDayFormSection } from '../features/race-day-form/race-day-form-section';
import { RunningDashboardPage } from '../features/running-dashboard/running-dashboard-page';

@Component({ selector: 'app-race-day-form-section', template: '' })
class RaceDayFormSectionStub {}

/** Swaps the race-day form card for an inert element, so page specs don't see its request. */
export function stubRaceDayFormSection(): void {
  TestBed.overrideComponent(RunningDashboardPage, {
    remove: { imports: [RaceDayFormSection] },
    add: { imports: [RaceDayFormSectionStub] },
  });
}
