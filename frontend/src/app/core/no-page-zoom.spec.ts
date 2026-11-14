import { blockPageZoom } from './no-page-zoom';

describe('blockPageZoom', () => {
  let doc: Document;

  beforeEach(() => {
    doc = document.implementation.createHTMLDocument('test');
    blockPageZoom(doc);
  });

  const dispatch = (event: Event) => {
    doc.dispatchEvent(event);
    return event.defaultPrevented;
  };

  it('R5 - touch browsers only pan the page', () => {
    expect(doc.documentElement.style.touchAction).toBe('pan-x pan-y');
  });

  it('R5 - blocks ctrl+wheel zoom but not scrolling', () => {
    expect(dispatch(new WheelEvent('wheel', { cancelable: true, ctrlKey: true }))).toBe(true);
    expect(dispatch(new WheelEvent('wheel', { cancelable: true }))).toBe(false);
  });

  it('R5 - blocks Safari gesture zoom', () => {
    expect(dispatch(new Event('gesturestart', { cancelable: true }))).toBe(true);
    expect(dispatch(new Event('gesturechange', { cancelable: true }))).toBe(true);
  });
});
