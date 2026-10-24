"use client";

import Link from "next/link";
import { useCallback, useEffect, useRef, useState } from "react";
import { api, type Doc } from "@/lib/api";

export const dotColor = (score: number) => (score >= 80 ? "bg-green-600" : score >= 40 ? "bg-amber-500" : "bg-red-500");

export default function Library() {
  const [docs, setDocs] = useState<Doc[] | null>(null);
  const [mastery, setMastery] = useState<Record<string, number>>({});
  const [error, setError] = useState("");
  const [uploading, setUploading] = useState(false);
  const input = useRef<HTMLInputElement>(null);

  const load = useCallback(() => {
    api.list().then(setDocs, () => setError("Backend not reachable. Is it running on :8080?"));
  }, []);

  useEffect(load, [load]);
  useEffect(() => { api.masteryByDocument().then(setMastery, () => {}); }, []);

  // Poll while any document is still being processed.
  const processing = docs?.some((d) => d.status === "PROCESSING");
  useEffect(() => {
    if (!processing) return;
    const t = setInterval(load, 2000);
    return () => clearInterval(t);
  }, [processing, load]);

  async function upload(files: FileList | null) {
    if (!files?.length) return;
    setUploading(true);
    setError("");
    try {
      for (const f of Array.from(files)) await api.upload(f);
    } catch {
      setError("Upload failed. Only .pdf files up to 100 MB.");
    }
    setUploading(false);
    load();
  }

  async function remove(d: Doc) {
    if (!confirm(`Delete "${d.title}"? Its pages will be removed too.`)) return;
    await api.remove(d.id);
    load();
  }

  const resume = docs?.find((d) => d.status === "READY");

  return (
    <main className="mx-auto w-full max-w-5xl px-4 py-6">
      <div className="flex items-center gap-3">
        <h1 className="text-2xl font-semibold">My PDFs</h1>
        <span className="flex-1" />
        <input ref={input} type="file" accept="application/pdf,.pdf" multiple hidden
               onChange={(e) => { upload(e.target.files); e.target.value = ""; }} />
        <button onClick={() => input.current?.click()} disabled={uploading}
                className="rounded-lg bg-pdf px-3 py-1.5 text-white disabled:opacity-50">
          {uploading ? "Uploading…" : "+ Upload PDF"}
        </button>
      </div>

      {error && <p className="mt-4 text-red-600">{error}</p>}

      {resume && (
        <Link href={`/read/${resume.id}`}
              className="mt-4 flex items-center gap-4 rounded-xl border border-border bg-surface px-4 py-3 hover:border-pdf">
          <span>
            <b>📄 {resume.title}</b>
            <span className="block text-muted">Page {resume.lastPage} of {resume.pageCount}</span>
          </span>
          <span className="flex-1" />
          <span className="text-pdf">Resume →</span>
        </Link>
      )}

      <div
        className="mt-6 grid gap-4 [grid-template-columns:repeat(auto-fill,minmax(180px,1fr))]"
        onDragOver={(e) => e.preventDefault()}
        onDrop={(e) => { e.preventDefault(); upload(e.dataTransfer.files); }}
      >
        {docs?.map((d) => (
          <div key={d.id} className="overflow-hidden rounded-xl border border-border bg-surface">
            <Link href={d.status === "READY" ? `/read/${d.id}` : "#"} className="block p-3 hover:bg-background">
              <b className="line-clamp-2">{d.title}</b>
              <span className="mt-1 block text-muted">
                {d.status === "READY" && `${d.pageCount} pages · read ${Math.round((100 * d.lastPage) / Math.max(1, d.pageCount))}%`}
                {d.status === "PROCESSING" && "◌ Processing…"}
                {d.status === "FAILED" && "⚠ Could not read this PDF"}
              </span>
              {mastery[d.id] !== undefined && (
                <span className="mt-1 flex items-center gap-1.5 text-xs">
                  <span className={`h-2 w-2 rounded-full ${dotColor(mastery[d.id])}`} /> {mastery[d.id]}% mastered
                </span>
              )}
            </Link>
            <button onClick={() => remove(d)} className="w-full border-t border-border py-1 text-xs text-muted hover:text-red-600">
              Delete
            </button>
          </div>
        ))}
        <button onClick={() => input.current?.click()}
                className="min-h-28 rounded-xl border-2 border-dashed border-border text-muted hover:border-pdf">
          ⬆ Drop a PDF here<br />or click to upload
        </button>
      </div>
      {docs?.length === 0 && <p className="mt-4 text-muted">No PDFs yet. Start with DDIA.</p>}
    </main>
  );
}
