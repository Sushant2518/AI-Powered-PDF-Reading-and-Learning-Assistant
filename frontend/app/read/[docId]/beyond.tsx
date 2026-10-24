"use client";

import { useEffect, useState } from "react";
import { api, type Beyond, type Source } from "@/lib/api";
import { notifyUsage } from "@/app/usage-meter";
import AiMarkdown from "@/app/markdown";
import { SaveNoteButton } from "./notes-tab";
import type { DeepenTarget } from "./deepen-tab";

/** Green "Beyond the PDF" block: never mixed with PDF content, every statement numbered to a source. */
export function BeyondBlock({ content, sources, hidden, accessedAt }: { content: string; sources: Source[]; hidden: number; accessedAt?: string }) {
  return (
    <div className="rounded-lg border border-border border-l-4 border-l-beyond bg-background p-3">
      <div className="mb-1 text-xs font-semibold uppercase tracking-wider text-beyond">🌐 Beyond the PDF</div>
      <p className="mb-3 text-xs text-muted">Not from your book. Every point links to its source.</p>
      <AiMarkdown text={content} sources={sources} />
      <ol className="mt-3 space-y-1 border-t border-border pt-2 text-xs">
        {sources.map((s) => (
          <li key={s.n} className="flex gap-1.5">
            <span className="text-beyond">[{s.n}]</span>
            <a href={s.url} target="_blank" rel="noopener noreferrer" className="hover:underline">
              {s.title} <span className="text-muted">· {hostname(s.url)}</span>
            </a>
          </li>
        ))}
      </ol>
      <p className="mt-2 text-xs text-muted">
        {accessedAt && `Accessed ${new Date(accessedAt).toLocaleDateString()}. `}
        {hidden > 0 && `${hidden} unsourced statement${hidden > 1 ? "s" : ""} hidden.`}
      </p>
    </div>
  );
}

/** Markdown with its sources listed, for saving to notes. */
export function beyondAsNote(title: string, content: string, sources: Source[]) {
  return `### 🌐 ${title}\n\n${content}\n\n${sources.map((s) => `${s.n}. [${s.title}](${s.url})`).join("\n")}`;
}

const hostname = (url: string) => { try { return new URL(url).hostname.replace(/^www\./, ""); } catch { return url; } };

type Props = {
  docId: string;
  target: DeepenTarget | null;
  onSaveNote: (page: number, content: string) => Promise<unknown>;
};

export default function BeyondTab({ docId, target, onSaveNote }: Props) {
  const [result, setResult] = useState<{ key: string; beyond?: Beyond; error?: string } | null>(null);
  const [nonce, setNonce] = useState(0);   // bumps on "Refresh" to force a new search
  const key = target ? `${target.concept.id}:${nonce}` : "";

  useEffect(() => {
    if (!target) return;
    let cancelled = false;
    // Deferred + cancellable so React's dev double-mount sends one web search, not two.
    const t = setTimeout(() => api.beyond(target.concept.id, docId, target.page, nonce > 0).then(
      (beyond) => { if (!cancelled) { setResult({ key, beyond }); notifyUsage(); } },
      (e: Error) => { if (!cancelled) setResult({ key, error: e.message }); }), 0);
    return () => { cancelled = true; clearTimeout(t); };
  }, [docId, target, nonce, key]);

  if (!target) {
    return <p className="text-muted">Pick a concept in 🔬 Deepen (or select a term on the page and choose 🧭 Beyond) to see what&apos;s beyond the book.</p>;
  }

  const current = result?.key === key ? result : null;

  return (
    <div>
      <h2 className="mb-3 text-lg font-semibold">{target.concept.name}</h2>
      {!current ? (
        <div className="space-y-2">
          <p className="text-muted">🔎 Searching trusted sources for {target.concept.name}… (10–40 s the first time)</p>
          {[80, 95, 70].map((w) => <div key={w} className="h-3 animate-pulse rounded bg-border" style={{ width: `${w}%` }} />)}
        </div>
      ) : current.error ? (
        <p className="text-red-600">{current.error}</p>
      ) : (
        <>
          <BeyondBlock {...current.beyond!} />
          <div className="mt-3 flex gap-4">
            <SaveNoteButton onSave={() => onSaveNote(target.page, beyondAsNote(target.concept.name, current.beyond!.content, current.beyond!.sources))} />
            <button onClick={() => setNonce((n) => n + 1)} className="text-xs text-muted hover:text-foreground">↻ Refresh sources</button>
          </div>
        </>
      )}
    </div>
  );
}
