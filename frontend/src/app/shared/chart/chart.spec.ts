import { NO_ERRORS_SCHEMA } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { ChartComponent } from './chart';

describe('ChartComponent', () => {
  it('R3 - chart element blocks browser pinch-zoom', () => {
    // ECharts can't render in jsdom: keep the real template, drop the directive.
    TestBed.overrideComponent(ChartComponent, {
      set: { imports: [], schemas: [NO_ERRORS_SCHEMA] },
    });
    const fixture = TestBed.createComponent(ChartComponent);
    fixture.componentRef.setInput('options', {});
    fixture.componentRef.setInput('label', 'Test chart');
    fixture.detectChanges();

    const chart: HTMLElement = fixture.nativeElement.querySelector('.chart');
    const scope = chart.getAttributeNames().find((name) => name.startsWith('_ngcontent'));
    // jsdom drops `touch-action` from computed styles, so match the rule scoped to this element.
    const css = Array.from(document.querySelectorAll('style'), (s) => s.textContent).join('');
    expect(css).toMatch(
      new RegExp(`\\.chart\\[${scope}\\]\\s*\\{[^}]*touch-action:\\s*pan-x pan-y`),
    );
  });
});
