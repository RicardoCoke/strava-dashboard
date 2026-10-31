import { Type } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { progress, trainingPlan } from '../../testing/training-plan-fixtures';
import { BuildCalendar } from './build-calendar/build-calendar';
import { BuildProgressBar } from './build-progress/build-progress-bar';
import { scrollTargetWeekIndex } from './plan-view';
import { TargetPaces } from './target-paces/target-paces';

function render<T>(component: Type<T>, inputs: Record<string, unknown>): HTMLElement {
  const fixture = TestBed.createComponent(component);
  Object.entries(inputs).forEach(([name, value]) => fixture.componentRef.setInput(name, value));
  fixture.detectChanges();
  return fixture.nativeElement as HTMLElement;
}

/** Child element texts joined by a space (Angular drops the whitespace between elements). */
const childTexts = (el: Element | null | undefined) =>
  Array.from(el?.children ?? [])
    .map((child) => child.textContent?.trim())
    .join(' ');

const squash = (el: Element | null | undefined) =>
  el?.textContent?.replace(/\s+/g, ' ').trim() ?? '';

describe('marathon build parts', () => {
  it('004 R10 - progressbar aria and label', () => {
    const element = render(BuildProgressBar, { progress: progress() });
    const bar = element.querySelector('[role="progressbar"]');
    expect(bar?.getAttribute('aria-valuemin')).toBe('0');
    expect(bar?.getAttribute('aria-valuemax')).toBe('100');
    expect(bar?.getAttribute('aria-valuenow')).toBe('66');
    expect(bar?.getAttribute('aria-valuetext')).toBe('66% · week 11 of 16 · 36 days to go');
    expect(squash(element.querySelector('.label'))).toBe('66% · week 11 of 16 · 36 days to go');
  });

  it('004 R10 - fill width follows percent', () => {
    [66, 0, 100].forEach((percentComplete) => {
      const element = render(BuildProgressBar, { progress: progress({ percentComplete }) });
      expect(element.querySelector<HTMLElement>('.fill')?.style.width).toBe(`${percentComplete}%`);
      expect(element.querySelector('.track')).not.toBeNull();
    });
  });

  it('004 R11 - single and range paces formatted', () => {
    const plan = trainingPlan();
    const element = render(TargetPaces, { paces: plan.targetPaces });
    expect(squash(element.querySelector('.title'))).toBe('Target paces');
    const rows = Array.from(element.querySelectorAll('.pace')).map((row) => childTexts(row));
    expect(rows).toEqual(['Easy 5:38 /km', '15K 4:00–4:10 /km target 4:05', '30K 4:15–4:22 /km']);
  });

  it('010 R10 - build calendar has no plan notes panel', () => {
    const element = render(TargetPaces, { paces: trainingPlan().targetPaces });
    expect(element.querySelector('.notes')).toBeNull();
    expect(element.textContent).not.toContain('Plan notes');
    expect(element.querySelectorAll('.pace').length).toBe(3);
  });

  it('004 R11 - panel hidden without paces', () => {
    const element = render(TargetPaces, { paces: [] });
    expect(element.querySelector('.paces')).toBeNull();
  });

  it('004 R12 - scroll target per phase', () => {
    const weeks = trainingPlan().weeks;
    expect(scrollTargetWeekIndex(progress(), weeks)).toBe(11);
    expect(
      scrollTargetWeekIndex(progress({ phase: 'notStarted', currentWeekIndex: null }), weeks),
    ).toBe(1);
    expect(
      scrollTargetWeekIndex(progress({ phase: 'finished', currentWeekIndex: null }), weeks),
    ).toBe(16);
  });
});

describe('BuildCalendar', () => {
  const plan = trainingPlan();
  const calendar = (weeks = plan.weeks) =>
    render(BuildCalendar, { weeks, progress: plan.progress });

  it('004 R12 - sixteen weeks Monday to Sunday', () => {
    const element = calendar();
    const weeks = element.querySelectorAll('.week');
    expect(weeks.length).toBe(16);
    weeks.forEach((week) => expect(week.querySelectorAll('.day').length).toBe(7));
    const dates = Array.from(element.querySelectorAll('.day .date')).map((d) => d.textContent);
    expect(dates[0]).toBe('Mon 17 Aug');
    expect(dates.at(-1)).toBe('Sun 6 Dec');
  });

  it('004 R12 - week summary', () => {
    const summary = childTexts(calendar().querySelector('.week .summary'));
    expect(summary).toBe('WEEK 1 62.40 km 79.25% easy · 20.75% subT');
    expect(squash(calendar().querySelectorAll('.week .week-label')[15])).toBe('RACE WEEK');
  });

  it('004 R12 - day cell content', () => {
    const tuesday = calendar().querySelectorAll('.week')[0].querySelectorAll('.day')[1];
    expect(squash(tuesday.querySelector('.date'))).toBe('Tue 18 Aug');
    expect(squash(tuesday.querySelector('.session'))).toBe("20' WU/CD + 4x8' subT");
    expect(squash(tuesday.querySelector('.day-note'))).toBe('hilly route');
    expect(squash(tuesday.querySelector('.numbers'))).toBe('50 min · 8.50 km');

    const noSession = plan.weeks.map((week) => ({
      ...week,
      days: week.days.map((d) => ({ ...d, session: '' })),
    }));
    const monday = calendar(noSession).querySelector('.day');
    expect(monday?.querySelector('.session')).toBeNull();
    expect(squash(monday?.querySelector('.numbers'))).toBe('50 min · 8.50 km');
  });

  it('004 R12 - rest day', () => {
    const rest = calendar().querySelectorAll('.day.rest');
    expect(rest.length).toBe(1);
    expect(squash(rest[0].querySelector('.session'))).toBe('Rest');
    expect(rest[0].querySelector('.numbers')).toBeNull();
  });

  it('004 R12 - races and single marathon highlight', () => {
    const element = calendar();
    const races = element.querySelectorAll('.day.race');
    expect(races.length).toBe(2);
    expect(squash(races[0].querySelector('.race-badge'))).toBe('5K race');
    const marathons = element.querySelectorAll('.day.marathon');
    expect(marathons.length).toBe(1);
    expect(marathons[0].classList).toContain('race');
    expect(squash(marathons[0].querySelector('.race-badge'))).toBe('Marathon · 42.2 km');
    expect(squash(marathons[0].querySelector('.session'))).toBe('RACE 42,2K');
  });

  it('004 R12 - today marked once', () => {
    const today = calendar().querySelectorAll('.day.today');
    expect(today.length).toBe(1);
    expect(today[0].getAttribute('aria-current')).toBe('date');
    expect(squash(today[0].querySelector('.date'))).toBe('Sat 31 Oct');
    expect(squash(today[0].querySelector('.badge'))).toBe('Today');
  });

  it('004 R12 - current week marked once', () => {
    const current = calendar().querySelectorAll('.week.current-week');
    expect(current.length).toBe(1);
    expect(current[0].getAttribute('aria-current')).toBe('true');
    expect(current[0].getAttribute('data-week-index')).toBe('11');
    expect(squash(current[0].querySelector('.tag'))).toBe('This week');
  });

  it('004 R12 - session and note rendered as text', () => {
    const weeks = plan.weeks.map((week, i) =>
      i === 0
        ? {
            ...week,
            days: [
              { ...week.days[0], session: '<b>x</b>', note: '<i>y</i>' },
              ...week.days.slice(1),
            ],
          }
        : week,
    );
    const monday = calendar(weeks).querySelector('.day');
    expect(monday?.querySelector('b')).toBeNull();
    expect(monday?.querySelector('.session')?.textContent).toBe('<b>x</b>');
    expect(monday?.querySelector('.day-note')?.textContent).toBe('<i>y</i>');
  });

  it('004 R17 - past days marked and today is not past', () => {
    const element = calendar();
    const past = element.querySelectorAll('.day.past');
    expect(past.length).toBe(75);
    expect(element.querySelector('.day.today')?.classList).not.toContain('past');
    expect(squash(past[0].querySelector('.date'))).toBe('Mon 17 Aug');
    expect(squash(past[past.length - 1].querySelector('.date'))).toBe('Fri 30 Oct');
    // Past races keep their class and badge (they only change style).
    expect(element.querySelector('.day.race.past .race-badge')?.textContent).toBe('5K race');
  });

  it('004 R17 - no past days before the build and all after it', () => {
    const future = plan.weeks.map((week) => ({
      ...week,
      days: week.days.map((d) => ({ ...d, isPast: false, isToday: false })),
    }));
    expect(calendar(future).querySelectorAll('.day.past').length).toBe(0);
    const done = plan.weeks.map((week) => ({
      ...week,
      days: week.days.map((d) => ({ ...d, isPast: true, isToday: false })),
    }));
    expect(calendar(done).querySelectorAll('.day.past').length).toBe(112);
  });

  it('004 R18 - day tiles are not interactive', () => {
    const tiles = Array.from(calendar().querySelectorAll('.day'));
    expect(tiles.length).toBe(112);
    tiles.forEach((tile) => {
      expect(tile.getAttribute('tabindex')).toBeNull();
      expect(tile.getAttribute('role')).toBeNull();
    });
  });
});
