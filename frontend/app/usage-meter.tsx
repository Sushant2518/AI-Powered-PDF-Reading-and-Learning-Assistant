"use client";

import { useEffect, useState } from "react";
import { api, type Usage } from "@/lib/api";

/** Gemini requests today, or Claude spend this month. Refreshes whenever an AI call finishes (`ai-usage` window event). */
export default function UsageMeter() {
  const [usage, setUsage] = useState<Usage | null>(null);

  useEffect(() => {
    const load = () => api.usage().then(setUsage, () => {});
    load();
    addEventListener("ai-usage", load);
    return () => removeEventListener("ai-usage", load);
  }, []);

  if (!usage) return null;
  if (usage.provider === "gemini") {
    return <span className="text-xs text-muted" title="Gemini free tier: requests made today">Gemini · free · {usage.geminiRequestsToday} today</span>;
  }
  const pct = Math.min(100, (100 * usage.spentUsd) / usage.budgetUsd);
  return (
    <span className="flex items-center gap-2 text-xs text-muted" title="Claude API spend this month">
      ${usage.spentUsd.toFixed(2)} / ${usage.budgetUsd.toFixed(0)}
      <span className="h-1.5 w-16 overflow-hidden rounded bg-border">
        <span className={`block h-full ${pct >= 80 ? "bg-amber-500" : "bg-pdf"}`} style={{ width: `${pct}%` }} />
      </span>
    </span>
  );
}

export const notifyUsage = () => dispatchEvent(new Event("ai-usage"));
