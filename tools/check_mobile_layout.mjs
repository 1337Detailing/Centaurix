/** Horizontal overflow regression across narrow Android viewport sizes. */
import { chromium } from "playwright";
import assert from "node:assert/strict";

const browser = await chromium.launch({ headless: true });
const port = process.env.SITE_PORT || "8765";
const url = `http://127.0.0.1:${port}/`;
try {
  for (const width of [320, 360, 375, 390, 412, 430, 768]) {
    const page = await browser.newPage({
      viewport: { width, height: 780 },
      deviceScaleFactor: 2,
      isMobile: true,
      hasTouch: true,
    });
    await page.goto(url, { waitUntil: "load" });
    await page.locator(".hero-visual img").waitFor({ state: "attached" });
    const metrics = await page.evaluate(() => ({
      viewport: document.documentElement.clientWidth,
      docWidth: document.documentElement.scrollWidth,
      bodyWidth: document.body.scrollWidth,
      screenWidth: window.innerWidth,
    }));
    assert.ok(metrics.docWidth <= width + 1,
      `Document overflows at ${width}px: ${JSON.stringify(metrics)}`);
    await page.evaluate(() => window.scrollTo({ left: 300, top: 0, behavior: "instant" }));
    const horizontalScroll = await page.evaluate(() => window.scrollX);
    assert.equal(horizontalScroll, 0,
      `Page should never pan horizontally at ${width}px`);

    // The deliberately horizontal screenshot carousel must remain usable.
    const strip = page.locator("#screenshots-strip");
    const canScroll = await strip.evaluate((e) => e.scrollWidth > e.clientWidth);
    if (width <= 430) {
      assert.ok(canScroll, `Carousel must remain scrollable at ${width}px`);
    }
    if (canScroll) {
      const before = await strip.evaluate((e) => e.scrollLeft);
      await strip.evaluate((e) => { e.scrollLeft += 125; });
      const after = await strip.evaluate((e) => e.scrollLeft);
      assert.ok(after > before, `Carousel cannot scroll at ${width}px`);
    }
    console.log(`PASS: ${width}px viewport — no horizontal page scroll, gallery works`);
    await page.close();
  }
} finally {
  await browser.close();
}
