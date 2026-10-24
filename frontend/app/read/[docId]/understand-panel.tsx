"use client";

import { useEffect, useState } from "react";
import { API, api, type PageText } from "@/lib/api";
import { parseSections } from "@/lib/sections";
import { useAiStream } from "@/lib/sse";
import { notifyUsage } from "@/app/usage-meter";
import AiMarkdown from "@/app/markdown";
import { SaveNoteButton } from "./notes-tab";
import type { SelectionCard } from "./reader";
import type { Prefs } from "@/lib/prefs";

const LANG_LABEL = { none: "", mr: "मराठी", hi: "हिन्दी" } as const;
type Tab = "simple" | "native" | "original";

type Props = {
  docId: string;
  page: number;
  prefs: Prefs;
  selection: SelectionCard | null;
  selectionStream: ReturnType<typeof useAiStream>;
  onCloseSelection: () => void;
  onSaveNote: (page: number, content: string, selectedText?: string) => Promise<unknown>;
  /** Called after the page was re-read from its image (OCR). */
  onOcr: () => void;
};

export default function UnderstandTab({ docId, page, prefs, selection, selectionStream, onCloseSelection, onSaveNote, onOcr }: Props) {
  const explain = useAiStream(notifyUsage);
  const { start: startExplain, reset: resetExplain } = explain;
  const [tab, setTab] = useState<Tab>("simple");
  const [original, setOriginal] = useState<PageText | null>(null);
  const [openTerm, setOpenTerm] = useState<string | null>(null);
  const url = `${API}/documents/${docId}/pages/${page}/understand?lang=${prefs.lang}&style=${prefs.style}`;

  // Auto-explain after the page has been open for a moment, so flipping through pages costs nothing.
  // Leaving the page (or changing language/style) cancels the stream, which also stops the AI call.
  useEffect(() => {
    const t = prefs.auto ? setTimeout(() => startExplain(url), 1200) : undefined;
    return () => { clearTimeout(t); resetExplain(); };
  }, [url, prefs.auto, startExplain, resetExplain]);

  useEffect(() => {
    if (tab === "original") api.page(docId, page).then(setOriginal, () => setOriginal(null));
  }, [tab, docId, page]);

  const { simple, native, terms } = parseSections(explain.text);
  const streaming = explain.status === "streaming";
  const activeTab = tab === "native" && prefs.lang === "none" ? "simple" : tab;
  const originalText = original?.pageNumber === page ? original.text : null;

  return (
    <div>
      {selection && (
        <div className="mb-5 rounded-lg border border-border border-l-4 border-l-pdf bg-background p-3">
          <div className="mb-1 text-xs font-semibold uppercase tracking-wider text-pdf">
            📄 Selection · {selection.action === "TRANSLATE" ? LANG_LABEL[prefs.lang] : "Explain"} · p.{selection.page}
          </div>
          <blockquote className="mb-2 italic text-muted line-clamp-3">❝{selection.text}❞</blockquote>
          {selectionStream.status === "error" ? (
            <p className="text-red-600">{selectionStream.error}</p>
          ) : selectionStream.text ? (
            <AiMarkdown text={selectionStream.text} streaming={selectionStream.status === "streaming"}
                        className={selection.action === "TRANSLATE" ? "native" : ""} />
          ) : (
            <p className="text-muted">Thinking…</p>
          )}
          <div className="mt-2 flex gap-4">
            {selectionStream.status === "done" && (
              <SaveNoteButton onSave={() => onSaveNote(selection.page, selectionStream.text, selection.text)} />
            )}
            <button onClick={onCloseSelection} className="text-xs text-muted hover:text-foreground">Close</button>
          </div>
        </div>
      )}

      <div className="mb-2 text-xs font-semibold uppercase tracking-wider text-pdf">📄 From your PDF · p.{page}</div>
      <div className="mb-3 inline-flex rounded-lg bg-background p-1">
        {(["simple", "native", "original"] as const)
          .filter((t) => t !== "native" || prefs.lang !== "none")
          .map((t) => (
            <button key={t} onClick={() => setTab(t)}
                    className={`rounded-md px-3 py-1 ${activeTab === t ? "bg-surface shadow-sm" : "text-muted"}`}>
              {t === "simple" ? "Simple English" : t === "native" ? LANG_LABEL[prefs.lang] : "Original"}
            </button>
          ))}
      </div>

      {activeTab === "original" ? (
        <>
          <pre className="whitespace-pre-wrap font-sans leading-relaxed">{originalText ?? "Loading…"}</pre>
          <div className="mt-3 border-t border-border pt-2 text-xs text-muted">
            Text looks like gibberish (old Marathi/Hindi fonts, scans)? <OcrButton docId={docId} page={page} onDone={onOcr} label="Re-read from the page image" small />
          </div>
        </>
      ) : explain.status === "idle" ? (
        prefs.auto
          ? <p className="text-muted">Explaining this page in a moment…</p>
          : <button onClick={() => startExplain(url)} className="rounded-lg bg-pdf px-3 py-1.5 text-white">Explain this page</button>
      ) : explain.status === "error" ? (
        <div>
          <p className="mb-2 text-red-600">{explain.error}</p>
          {explain.error.includes("needs OCR")
            ? <OcrButton docId={docId} page={page} onDone={onOcr} label="📷 Read this page from its image" />
            : <button onClick={() => startExplain(url)} className="rounded-lg border border-border px-3 py-1">Try again</button>}
        </div>
      ) : !explain.text ? (
        <div className="space-y-2" aria-label="Loading">
          <p className="text-muted">Reading p.{page}…</p>
          {[90, 100, 75, 95].map((w) => <div key={w} className="h-3 animate-pulse rounded bg-border" style={{ width: `${w}%` }} />)}
        </div>
      ) : (
        <>
          <AiMarkdown text={activeTab === "native" ? native || "…" : simple} streaming={streaming}
                      className={activeTab === "native" ? "native" : ""} />

          {terms.length > 0 && (
            <>
              <div className="mt-4 mb-1 text-xs uppercase tracking-wider text-muted">Key terms</div>
              <div className="flex flex-wrap gap-1.5">
                {terms.map((t) => (
                  <button key={t.term} onClick={() => setOpenTerm(openTerm === t.term ? null : t.term)}
                          className={`rounded-full border px-2.5 py-0.5 ${openTerm === t.term ? "border-pdf" : "border-border"}`}>
                    {t.term}
                  </button>
                ))}
              </div>
              {openTerm && <p className="mt-2 rounded-lg bg-background p-2">{terms.find((t) => t.term === openTerm)?.meaning}</p>}
            </>
          )}

          {explain.status === "done" && (
            <div className="mt-4 flex gap-4">
              <SaveNoteButton onSave={() => onSaveNote(page, activeTab === "native" ? native : simple)} />
              <button onClick={() => startExplain(`${url}&regenerate=true`)} className="text-xs text-muted hover:text-foreground">
                ↻ Regenerate
              </button>
            </div>
          )}
        </>
      )}
    </div>
  );
}

function OcrButton({ docId, page, onDone, label, small = false }: { docId: string; page: number; onDone: () => void; label: string; small?: boolean }) {
  const [state, setState] = useState<{ busy: boolean; error?: string }>({ busy: false });
  async function run() {
    setState({ busy: true });
    try {
      await api.ocr(docId, page);
      notifyUsage();
      onDone();
    } catch (e) {
      setState({ busy: false, error: (e as Error).message });
    }
  }
  return (
    <>
      <button onClick={run} disabled={state.busy}
              className={small ? "text-pdf underline disabled:opacity-50" : "rounded-lg bg-pdf px-3 py-1.5 text-white disabled:opacity-50"}>
        {state.busy ? "Reading the page image…" : label}
      </button>
      {state.error && <p className="mt-2 text-red-600">{state.error}</p>}
    </>
  );
}

