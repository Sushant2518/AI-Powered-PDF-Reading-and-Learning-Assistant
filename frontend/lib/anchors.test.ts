import { test } from "node:test";
import assert from "node:assert/strict";
import { normaliseRects } from "./anchors.ts";

const page = { left: 100, top: 50, width: 500, height: 1000 };

test("normalises to the page and survives zoom", () => {
  assert.deepEqual(normaliseRects([{ left: 150, top: 150, width: 250, height: 20 }], page), [{ x: 0.1, y: 0.1, w: 0.5, h: 0.02 }]);
  const zoomed = { left: 100, top: 50, width: 1000, height: 2000 };
  assert.deepEqual(normaliseRects([{ left: 200, top: 250, width: 500, height: 40 }], zoomed), [{ x: 0.1, y: 0.1, w: 0.5, h: 0.02 }]);
});

test("drops empty, off-page and nested duplicate rects", () => {
  const rects = normaliseRects([
    { left: 150, top: 150, width: 250, height: 20 },
    { left: 160, top: 152, width: 100, height: 10 }, // inside the first one
    { left: 150, top: 150, width: 0, height: 20 },   // empty
    { left: 10, top: 10, width: 50, height: 10 },    // outside the page
  ], page);
  assert.equal(rects.length, 1);
});
