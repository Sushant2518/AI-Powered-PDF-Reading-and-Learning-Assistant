"use client";

import Link from "next/link";
import { useEffect, useState } from "react";
import { api, DOMAIN_LABEL, type Dashboard, type Doc } from "@/lib/api";
import { dotColor } from "@/app/library";

const barColor = (score: number) => (score >= 80 ? "bg-green-600" : score >= 40 ? "bg-amber-500" : "bg-red-500");

export default function MasteryPage() {
  const [docs, setDocs] = useState<Doc[]>([]);
  const [documentId, setDocumentId] = useState("");
  const [data, setData] = useState<{ key: string; dashboard?: Dashboard; error?: string } | null>(null);

  useEffect(() => { api.list().then(setDocs, () => {}); }, []);
  useEffect(() => {
    api.mastery(documentId || undefined).then(
      (dashboard) => setData({ key: documentId, dashboard }),
      (e: Error) => setData({ key: documentId, error: e.message }));
  }, [documentId]);

  const d = data?.key === documentId ? data.dashboard : undefined;

  return (
    <main className="mx-auto w-full max-w-5xl px-4 py-6">
      <div className="mb-5 flex flex-wrap items-center gap-3">
        <h1 className="text-2xl font-semibold">📈 Mastery</h1>
        <span className="flex-1" />
        <select value={documentId} onChange={(e) => setDocumentId(e.target.value)}
                className="rounded-lg border border-border bg-surface px-2 py-1" aria-label="Filter by PDF">
          <option value="">All PDFs</option>
          {docs.map((doc) => <option key={doc.id} value={doc.id}>{doc.title}</option>)}
        </select>
      </div>

      {data?.error && <p className="text-red-600">{data.error}</p>}
      {!d ? <p className="text-muted">Loading…</p> : d.concepts.length === 0 ? (
        <p className="rounded-xl border border-border bg-surface p-4">No concepts yet. Open a PDF, use 🔬 Deepen, then 🎯 Practise.</p>
      ) : (
        <>
          <div className="mb-5 grid gap-4 sm:grid-cols-3">
            <Stat label="Overall mastery" value={`${d.overall}%`} />
            <Stat label="Concepts mastered" value={`${d.mastered} / ${d.concepts.length}`} />
            <Link href={`/practice${documentId ? `?documentId=${documentId}` : ""}`} className="rounded-xl border border-border bg-surface p-4 hover:border-pdf">
              <div className="text-xs uppercase tracking-wider text-muted">Flashcards due</div>
              <div className="text-2xl font-semibold">{d.dueFlashcards} →</div>
            </Link>
          </div>

          <div className="grid gap-4 md:grid-cols-2">
            <section className="rounded-xl border border-border bg-surface p-4">
              <h2 className="mb-3 font-semibold">By domain</h2>
              {d.domains.map((dom) => (
                <div key={dom.domain} className="mb-2 flex items-center gap-3">
                  <span className="w-32 truncate">{DOMAIN_LABEL[dom.domain] ?? dom.domain}</span>
                  <span className="h-2 flex-1 overflow-hidden rounded bg-border">
                    <span className={`block h-full ${barColor(dom.score)}`} style={{ width: `${dom.score}%` }} />
                  </span>
                  <span className="w-20 text-right text-muted">{dom.score}% · {dom.concepts}</span>
                </div>
              ))}
            </section>

            <section className="rounded-xl border border-border bg-surface p-4">
              <div className="mb-3 flex items-center">
                <h2 className="font-semibold">Weakest concepts</h2>
                <span className="flex-1" />
                {d.weakest.length > 0 && (
                  <Link href={`/practice${documentId ? `?documentId=${documentId}` : ""}`} className="text-xs text-pdf hover:underline">Practise all weak →</Link>
                )}
              </div>
              {d.weakest.length === 0 ? <p className="text-muted">Practise something first.</p> : d.weakest.map((c) => (
                <div key={c.id} className="flex items-center gap-2 border-b border-border py-2 last:border-0">
                  <span className={`h-2 w-2 rounded-full ${dotColor(c.score)}`} />
                  <span className="flex-1">{c.name}</span>
                  <b>{c.score}</b>
                  <Link href={`/practice?conceptIds=${c.id}${documentId ? `&documentId=${documentId}` : ""}`}
                        className="rounded-lg border border-border px-2 py-0.5 text-xs hover:border-pdf">Fix</Link>
                </div>
              ))}
            </section>
          </div>

          <section className="mt-4 rounded-xl border border-border bg-surface p-4">
            <h2 className="mb-3 font-semibold">All concepts</h2>
            <div className="grid gap-x-6 sm:grid-cols-2">
              {d.concepts.map((c) => (
                <div key={c.id} className="flex items-center gap-2 border-b border-border py-1.5">
                  <span className={`h-2 w-2 rounded-full ${c.attempts ? dotColor(c.score) : "bg-border"}`} />
                  <span className="flex-1 truncate">{c.name}</span>
                  <span className="text-xs text-muted">{c.stage.toLowerCase()}</span>
                  <b className="w-8 text-right">{c.score}</b>
                </div>
              ))}
            </div>
          </section>
        </>
      )}
    </main>
  );
}

function Stat({ label, value }: { label: string; value: string }) {
  return (
    <div className="rounded-xl border border-border bg-surface p-4">
      <div className="text-xs uppercase tracking-wider text-muted">{label}</div>
      <div className="text-2xl font-semibold">{value}</div>
    </div>
  );
}
