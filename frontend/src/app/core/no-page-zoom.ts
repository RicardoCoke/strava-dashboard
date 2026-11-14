/**
 * Feature 024 R5: the page can't be zoomed by accident in any browser, so a pinch meant for a chart never zooms
 * the page. Ctrl/Cmd + plus/minus and the browser menu still zoom on purpose.
 */
export function blockPageZoom(doc: Document): void {
  // Touch browsers: scroll only, no pinch or double-tap zoom. Children can't widen this, so it covers the charts too.
  doc.documentElement.style.touchAction = 'pan-x pan-y';
  // Desktop trackpad pinch and Ctrl + wheel arrive as a wheel event with ctrlKey set.
  doc.addEventListener('wheel', (e) => e.ctrlKey && e.preventDefault(), { passive: false });
  // macOS Safari sends its own gesture events for a trackpad pinch.
  for (const type of ['gesturestart', 'gesturechange']) {
    doc.addEventListener(type, (e) => e.preventDefault());
  }
}
