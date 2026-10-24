"use client";

/** Per-browser reading preferences (the AI budget lives on the server, see Settings). */
export type Prefs = {
  lang: "none" | "mr" | "hi";
  style: "VERY_SIMPLE" | "SIMPLE" | "DETAILED";
  auto: boolean;
  theme: "system" | "light" | "dark";
};

export const DEFAULT_PREFS: Prefs = { lang: "mr", style: "SIMPLE", auto: true, theme: "system" };

export function loadPrefs(): Prefs {
  try {
    return { ...DEFAULT_PREFS, ...JSON.parse(localStorage.getItem("prefs") ?? "{}") };
  } catch {
    return DEFAULT_PREFS;
  }
}

export function savePrefs(prefs: Prefs) {
  try { localStorage.setItem("prefs", JSON.stringify(prefs)); } catch {}
  applyTheme(prefs.theme);
}

/** "system" removes the attribute so the CSS media query decides. */
export function applyTheme(theme: Prefs["theme"]) {
  if (theme === "system") delete document.documentElement.dataset.theme;
  else document.documentElement.dataset.theme = theme;
}

export const isDark = () =>
  document.documentElement.dataset.theme === "dark" ||
  (!document.documentElement.dataset.theme && matchMedia("(prefers-color-scheme: dark)").matches);
