import { HttpErrorResponse } from '@angular/common/http';
import { efficiencyFactor } from '../../testing/efficiency-factor-fixtures';
import {
  changeText,
  changeTone,
  efStateFromError,
  emptyText,
  footnoteText,
} from './efficiency-factor.view';

const change = (percent: number) => ({
  percent,
  fromDate: '2026-05-03',
  fromEfficiency: 1.585,
  toDate: '2026-10-29',
  toEfficiency: 1.652,
});

describe('efficiency factor view helpers', () => {
  it('014 R10 - change text and tone for up, down and flat', () => {
    expect(changeText(change(4.2))).toBe('+4.2% since 3 May');
    expect(changeTone(change(4.2))).toBe('better');
    expect(changeText(change(-5))).toBe('-5.0% since 3 May');
    expect(changeTone(change(-5))).toBe('worse');
    expect(changeText(change(0))).toBe('0.0% since 3 May');
    expect(changeTone(change(0))).toBe('neutral');
  });

  it('014 R10 - footnote with pending line', () => {
    expect(footnoteText(efficiencyFactor())).toEqual([
      'EF = average speed (m/min) ÷ average heart rate. Easy and long outdoor runs of 20 min or more ' +
        'with heart rate; treadmill runs are left out.',
    ]);
    expect(footnoteText(efficiencyFactor({ runsPendingDetail: 1 }))[1]).toBe(
      '1 run still importing moving time; numbers will update.',
    );
    expect(footnoteText(efficiencyFactor({ runsPendingDetail: 4 }))[1]).toBe(
      '4 runs still importing moving time; numbers will update.',
    );
  });

  it('014 R11 - empty text only without points', () => {
    expect(emptyText(efficiencyFactor())).toBeNull();
    expect(emptyText(efficiencyFactor({ points: [], runsPendingDetail: 1 }))).toBe(
      "Moving time is still importing for 1 run. The chart will appear when it's done.",
    );
  });

  it('014 R11 - 401 leaves the card alone, other failures are errors', () => {
    expect(efStateFromError(new HttpErrorResponse({ status: 401 }))).toBeNull();
    expect(efStateFromError(new HttpErrorResponse({ status: 500 }))).toEqual({ kind: 'error' });
    expect(efStateFromError(new Error('x'))).toEqual({ kind: 'error' });
  });
});
