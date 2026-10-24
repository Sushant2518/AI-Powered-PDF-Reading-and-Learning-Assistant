"use client";

import { useEffect, useState } from "react";
import { api, type Settings } from "@/lib/api";
import { DEFAULT_PREFS, loadPrefs, savePrefs, type Prefs } from "@/lib/prefs";
import { notifyUsage } from "@/app/usage-meter";

export default function SettingsPage() {
  const [server, setServer] = useState<Settings | null>(null);
  const [prefs, setPrefs] = useState<Prefs>(() => (typeof window === "undefined" ? DEFAULT_PREFS : loadPrefs()));
  const [status, setStatus] = useState("");

  useEffect(() => { api.settings().then(setServer, (e: Error) => setStatus(e.message)); }, []);

  function updatePrefs(p: Prefs) {
    setPrefs(p);
    savePrefs(p);
  }

  async function save() {
    if (!server) return;
    setStatus("Saving…");
    try {
      setServer(await api.saveSettings({ monthlyBudgetUsd: server.monthlyBudgetUsd }));
      notifyUsage();
      setStatus("✓ Saved");
    } catch (e) {
      setStatus((e as Error).message);
    }
  }

  return (
    <main className="mx-auto w-full max-w-2xl space-y-5 px-4 py-6">
      <h1 className="text-2xl font-semibold">⚙ Settings</h1>

      <Section title="Reading" hint="Saved in this browser.">
        <Row label="Your language">
          <select value={prefs.lang} onChange={(e) => updatePrefs({ ...prefs, lang: e.target.value as Prefs["lang"] })} className={input}>
            <option value="mr">मराठी (Marathi)</option>
            <option value="hi">हिन्दी (Hindi)</option>
            <option value="none">English only</option>
          </select>
        </Row>
        <Row label="Explanation style">
          <select value={prefs.style} onChange={(e) => updatePrefs({ ...prefs, style: e.target.value as Prefs["style"] })} className={input}>
            <option value="VERY_SIMPLE">Very simple</option>
            <option value="SIMPLE">Simple</option>
            <option value="DETAILED">Detailed</option>
          </select>
        </Row>
        <Row label="Auto-explain pages">
          <input type="checkbox" checked={prefs.auto} onChange={(e) => updatePrefs({ ...prefs, auto: e.target.checked })} />
        </Row>
        <Row label="Theme">
          <select value={prefs.theme} onChange={(e) => updatePrefs({ ...prefs, theme: e.target.value as Prefs["theme"] })} className={input}>
            <option value="system">Same as system</option>
            <option value="light">Light</option>
            <option value="dark">Dark</option>
          </select>
        </Row>
      </Section>

      {!server ? <p className="text-muted">{status || "Loading…"}</p> : (
        <Section title="Claude" hint={`Model: ${server.claudeModel}. Needs ANTHROPIC_API_KEY in backend/.env.`}>
          <Row label="Monthly Claude budget ($)">
            <input type="number" min={0} max={1000} step={1} value={server.monthlyBudgetUsd}
                   onChange={(e) => setServer({ ...server, monthlyBudgetUsd: Number(e.target.value) })} className={`${input} w-28`} />
          </Row>

          <div className="mt-3 flex items-center gap-3">
            <button onClick={save} className="rounded-lg bg-pdf px-4 py-1.5 text-white">Save</button>
            <span className="text-muted">{status}</span>
          </div>
        </Section>
      )}

      {server && (
        <Section title="Trusted sources for 🌐 Beyond the PDF" hint="Edit samajh.ai.trusted-domains in backend/src/main/resources/application.yml.">
          <p className="text-xs leading-relaxed text-muted">{server.trustedDomains.join(" · ")}</p>
        </Section>
      )}
    </main>
  );
}

const input = "rounded-lg border border-border bg-background px-2 py-1";

function Section({ title, hint, children }: { title: string; hint?: string; children: React.ReactNode }) {
  return (
    <section className="rounded-xl border border-border bg-surface p-4">
      <h2 className="font-semibold">{title}</h2>
      {hint && <p className="mb-3 text-xs text-muted">{hint}</p>}
      <div className="space-y-2">{children}</div>
    </section>
  );
}

function Row({ label, children }: { label: string; children: React.ReactNode }) {
  return (
    <label className="flex items-center justify-between gap-3">
      <span>{label}</span>
      {children}
    </label>
  );
}
