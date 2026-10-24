import type { Rect } from "./api";

type Box = { left: number; top: number; width: number; height: number };

/**
 * Converts a selection's screen rectangles into rectangles relative to the page (0..1),
 * dropping empty ones and the duplicates browsers report for nested spans.
 */
export function normaliseRects(rects: Box[], page: Box): Rect[] {
  const out: Rect[] = [];
  for (const r of rects) {
    if (r.width < 1 || r.height < 1) continue;
    const rect = {
      x: round((r.left - page.left) / page.width),
      y: round((r.top - page.top) / page.height),
      w: round(r.width / page.width),
      h: round(r.height / page.height),
    };
    if (rect.x < 0 || rect.y < 0 || rect.x > 1 || rect.y > 1) continue;
    const covered = out.some((o) => rect.x >= o.x && rect.y >= o.y && rect.x + rect.w <= o.x + o.w + 1e-4 && rect.y + rect.h <= o.y + o.h + 1e-4);
    if (!covered) out.push(rect);
  }
  return out;
}

const round = (n: number) => Math.round(n * 10000) / 10000;
