"use client";

import { useEffect, useState } from "react";
import Link from "next/link";
import { api, DOMAIN_LABEL, type Concept } from "@/lib/api";
import { useAiStream } from "@/lib/sse";
import { notifyUsage } from "@/app/usage-meter";
import AiMarkdown from "@/app/markdown";
import { SaveNoteButton } from "./notes-tab";

export type DeepenTarget = { concept: Concept; page: number };

type Props = {
  docId: string;
  page: number;
  target: DeepenTarget | null;
  onSelect: (t: DeepenTarget) => void;
  onSaveNote: (page: number, content: string) => Promise<unknown>;
};

export default function DeepenTab({ docId, page, target, onSelect, onSaveNote }: Props) {
  const [concepts, setConcepts] = useState<{ page: number; list?: Concept[]; error?: string } | null>(null);
  const deepen = useAiStream(notifyUsage);
  const { start, reset } = deepen;
  const url = target ? api.deepenUrl(target.concept.id, docId, target.page) : null;

  // List the page's concepts (generated once per page, then cached on the server).
  useEffect(() => {
    let cancelled = false;
    const t = setTimeout(() => api.concepts(docId, page).then(
      (list) => !cancelled && setConcepts({ page, list }),
      (e: Error) => !cancelled && setConcepts({ page, error: e.message })), 600);
    return () => { cancelled = true; clearTimeout(t); };
  }, [docId, page]);

  useEffect(() => {
    if (!url) return;
    const t = setTimeout(() => start(url), 0);
    return () => { clearTimeout(t); reset(); };
  }, [url, start, reset]);

  const current = concepts?.page === page ? concepts : null;

  return (
    <div>
      <div className="mb-2 text-xs font-semibold uppercase tracking-wider text-pdf">📄 Concepts on p.{page}</div>
      {!current ? (
        <p className="text-muted">Finding concepts on p.{page}…</p>
      ) : current.error ? (
        <p className="text-red-600">{current.error}</p>
      ) : current.list!.length === 0 ? (
        <p className="text-muted">Nothing technical on this page (e.g. preface or index). Select a term and choose 🔬 Deepen.</p>
      ) : (
        <div className="flex flex-wrap items-center gap-1.5">
          {current.list!.map((c) => (
            <button key={c.id} onClick={() => onSelect({ concept: c, page })} title={c.definition}
                    className={`rounded-full border px-2.5 py-0.5 ${target?.concept.id === c.id ? "border-pdf bg-background" : "border-border"}`}>
              {c.name}
            </button>
          ))}
          <Link href={`/practice?documentId=${docId}&conceptIds=${current.list!.map((c) => c.id).join(",")}`}
                className="ml-auto text-xs text-pdf hover:underline">🎯 Practise this page</Link>
        </div>
      )}

      {target && (
        <article className="mt-5 border-t border-border pt-4">
          <div className="mb-2 flex items-baseline gap-2">
            <h2 className="text-lg font-semibold">{target.concept.name}</h2>
            {target.concept.domain !== "UNSORTED" && (
              <span className="rounded-full bg-background px-2 text-xs text-pdf">{DOMAIN_LABEL[target.concept.domain]}</span>
            )}
            {target.page !== page && <span className="text-xs text-muted">from p.{target.page}</span>}
          </div>
          <p className="mb-3 text-xs text-muted">
            “What it is” comes from your PDF. Code, diagram, trade-offs and mistakes are generated examples. Verify before relying on them.
          </p>
          {deepen.status === "error" ? (
            <p className="text-red-600">{deepen.error}</p>
          ) : !deepen.text ? (
            <p className="text-muted">Deepening {target.concept.name}…</p>
          ) : (
            <AiMarkdown text={deepen.text} streaming={deepen.status === "streaming"} />
          )}
          {deepen.status === "done" && (
            <div className="mt-3 flex gap-4">
              <SaveNoteButton onSave={() => onSaveNote(target.page, `### ${target.concept.name}\n\n${deepen.text}`)} />
              <button onClick={() => start(`${url}&regenerate=true`)} className="text-xs text-muted hover:text-foreground">↻ Regenerate</button>
              <Link href={`/practice?documentId=${docId}&conceptIds=${target.concept.id}`} className="text-xs text-pdf hover:underline">🎯 Practise {target.concept.name}</Link>
            </div>
          )}
        </article>
      )}
    </div>
  );
}
