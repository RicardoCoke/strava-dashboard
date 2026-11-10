import { planDay, planWeek, trainingPlan } from '../../testing/training-plan-fixtures';
import { isTrainingPlanResponse, weekRows } from './plan-view';

describe('weekRows (019)', () => {
  const withoutSplit = planWeek(1, {
    easyPercent: 0,
    subtPercent: 0,
    days: [planDay({ date: '2026-11-20', session: 'LT 13-14 km', totalMinutes: 0 })],
  });

  it('R9 - no split text and no minutes for a week without split', () => {
    const [row] = weekRows([withoutSplit], [], 'marina');
    expect(row.splitText).toBeNull();
    expect(row.days[0].minutesText).toBeNull();
    expect(row.days[0].kmText).toContain('km');
  });

  it('R9 - marina has no pinned workouts', () => {
    const [row] = weekRows([withoutSplit], [], 'marina');
    expect(row.days[0].session).toBe('LT 13-14 km');
    expect(row.days[0].isKeyWorkout).toBe(false);
  });

  it('R9 - ricardo keeps pinned workouts with the plan text', () => {
    const week = planWeek(1, {
      days: [planDay({ date: '2026-11-19', session: "5x20' subT", totalMinutes: 50 })],
    });
    const [row] = weekRows([week]);
    expect(row.days[0].session).toBe("5x20' subT");
    expect(row.days[0].isKeyWorkout).toBe(true);
    expect(row.days[0].minutesText).toBe('50 min');
    expect(row.splitText).toContain('% easy');
  });
});

describe('isTrainingPlanResponse', () => {
  it('010 R10 - plan shape check accepts body without notes', () => {
    const plan = trainingPlan();
    expect('notes' in plan).toBe(false);
    expect(isTrainingPlanResponse(plan)).toBe(true);
    expect(isTrainingPlanResponse({ ...plan, weeks: 'x' })).toBe(false);
  });
});
