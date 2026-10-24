"use client";

import Link from "next/link";
import dynamic from "next/dynamic";
import { useCallback, useEffect, useRef, useState } from "react";
import { API, api, type Doc, type Highlight, type HighlightColor, type Note, type Rect } from "@/lib/api";
import { normaliseRects } from "@/lib/anchors";
import { useAiStream } from "@/lib/sse";
import { notifyUsage } from "@/app/usage-meter";
import UnderstandTab from "./understand-panel";
import DeepenTab, { type DeepenTarget } from "./deepen-tab";
import NotesTab, { HIGHLIGHT_CSS } from "./notes-tab";
import BeyondTab from "./beyond";
import AskTab from "./ask-tab";
import type { Overlay } from "./pdf-view";
import { DEFAULT_PREFS, loadPrefs, savePrefs, type Prefs } from "@/lib/prefs";

const PdfView = dynamic(() => import("./pdf-view"), { ssr: false });

export type { Prefs } from "@/lib/prefs";
export type SelectionCard = { text: string; action: "EXPLAIN" | "TRANSLATE"; page: number };
type Menu = { x: number; y: number; text: string; rects: Rect[] };
type Tab = "understand" | "deepen" | "beyond" | "ask" | "notes";
const TAB_LABEL: Record<Tab, string> = { understand: "Understand", deepen: "🔬 Deepen", beyond: "🌐 Beyond", ask: "💬 Ask", notes: "📝 Notes" };

const COLORS: HighlightColor[] = ["yellow", "green", "blue", "pink"];

export default function Reader({ docId }: { docId: string }) {
  const [doc, setDoc] = useState<Doc | null>(null);
  const [error, setError] = useState("");
  const [page, setPage] = useState(1);
  const [zoom, setZoom] = useState(1);
  const [paneWidth, setPaneWidth] = useState(600);
  const [prefs, setPrefs] = useState<Prefs>(() => (typeof window === "undefined" ? DEFAULT_PREFS : loadPrefs()));
  const [tab, setTab] = useState<Tab>("understand");
  const [menu, setMenu] = useState<Menu | null>(null);
  const [selection, setSelection] = useState<SelectionCard | null>(null);
  const [deepen, setDeepen] = useState<DeepenTarget | null>(null);
  const [quote, setQuote] = useState<string | null>(null);
  const [notes, setNotes] = useState<Note[]>([]);
  const [highlights, setHighlights] = useState<Highlight[]>([]);
  const [editingId, setEditingId] = useState<number | null>(null);
  const [flash, setFlash] = useState<{ page: number; rects: Rect[] } | null>(null);
  const [toast, setToast] = useState("");
  const [ocrNonce, setOcrNonce] = useState(0);   // bumps after OCR so the Understand tab reloads
  const selectionStream = useAiStream(notifyUsage);
  const pane = useRef<HTMLDivElement>(null);

  useEffect(() => {
    api.get(docId).then((d) => { setDoc(d); setPage(d.lastPage); }, () => setError("Document not found."));
    api.notes(docId).then(setNotes, () => {});
    api.highlights(docId).then(setHighlights, () => {});
  }, [docId]);

  useEffect(() => savePrefs(prefs), [prefs]);

  // Fit the page to the pane width.
  useEffect(() => {
    if (!pane.current) return;
    const ro = new ResizeObserver(([e]) => setPaneWidth(e.contentRect.width));
    ro.observe(pane.current);
    return () => ro.disconnect();
  }, [doc]);

  // Remember the reading position (debounced).
  useEffect(() => {
    if (!doc) return;
    const t = setTimeout(() => api.patch(docId, { lastPage: page }), 500);
    return () => clearTimeout(t);
  }, [doc, docId, page]);

  useEffect(() => {
    if (!toast) return;
    const t = setTimeout(() => setToast(""), 2500);
    return () => clearTimeout(t);
  }, [toast]);

  const pages = doc?.pageCount ?? 1;
  const go = (n: number) => { setMenu(null); setPage(Math.min(pages, Math.max(1, n))); };

  useEffect(() => {
    const onKey = (e: KeyboardEvent) => {
      if (e.target instanceof HTMLInputElement || e.target instanceof HTMLSelectElement || e.target instanceof HTMLTextAreaElement) return;
      if (e.key === "ArrowRight") { setMenu(null); setPage((p) => Math.min(pages, p + 1)); }
      if (e.key === "ArrowLeft") { setMenu(null); setPage((p) => Math.max(1, p - 1)); }
    };
    addEventListener("keydown", onKey);
    return () => removeEventListener("keydown", onKey);
  }, [pages]);

  function onSelect() {
    const sel = getSelection();
    const text = sel?.toString().trim() ?? "";
    const box = pane.current?.querySelector("[data-page-box]");
    if (!sel || !text || !box || !pane.current?.contains(sel.anchorNode)) return setMenu(null);
    const range = sel.getRangeAt(0);
    const r = range.getBoundingClientRect();
    const rects = normaliseRects([...range.getClientRects()], box.getBoundingClientRect());
    setMenu({ x: r.left + r.width / 2, y: r.top, text: text.slice(0, 4000), rects });
  }

  function closeMenu() {
    setMenu(null);
    getSelection()?.removeAllRanges();
  }

  function runSelection(action: SelectionCard["action"]) {
    if (!menu) return;
    setSelection({ text: menu.text, action, page });
    setTab("understand");
    selectionStream.start(`${API}/ai/selection`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ documentId: docId, page, text: menu.text, action, lang: prefs.lang, style: prefs.style }),
    });
    closeMenu();
  }

  async function deepenSelection(then: Tab) {
    if (!menu) return;
    const name = menu.text;
    closeMenu();
    try {
      setDeepen({ concept: await api.createConcept(docId, page, name), page });
      setTab(then);
    } catch (e) {
      setToast((e as Error).message);
    }
  }

  function askAboutSelection() {
    if (!menu) return;
    setQuote(menu.text);
    setTab("ask");
    closeMenu();
  }

  async function noteFromSelection() {
    if (!menu) return;
    const { text, rects } = menu;
    closeMenu();
    const note = await api.addNote(docId, { page, selectedText: text, anchor: { rects } });
    setNotes((all) => [...all, note]);
    setEditingId(note.id);
    setTab("notes");
  }

  async function highlightSelection(color: HighlightColor) {
    if (!menu) return;
    const { text, rects } = menu;
    closeMenu();
    const h = await api.addHighlight(docId, { page, selectedText: text, anchor: { rects }, color });
    setHighlights((all) => [...all, h]);
  }

  const updateNote = useCallback((n: Note, content: string) =>
    api.updateNote(n.id, content).then((u) => setNotes((all) => all.map((x) => (x.id === u.id ? u : x)))), []);

  async function saveAiNote(notePage: number, content: string, selectedText?: string) {
    const note = await api.addNote(docId, { page: notePage, content, selectedText, source: "AI" });
    setNotes((all) => [...all, note]);
  }

  function jump(target: number, rects?: Rect[]) {
    go(target);
    if (rects?.length) {
      setFlash({ page: target, rects });
      setTimeout(() => setFlash(null), 1800);
    }
  }

  if (error) return <p className="p-6 text-red-600">{error} <Link href="/" className="text-pdf">Back to library</Link></p>;
  if (!doc) return <p className="p-6 text-muted">Loading…</p>;

  const overlays: Overlay[] = [
    ...highlights.filter((h) => h.pageNumber === page).map((h) => ({ key: `h${h.id}`, rects: h.anchor.rects, color: HIGHLIGHT_CSS[h.color] })),
    ...(flash?.page === page ? [{ key: "flash", rects: flash.rects, color: "rgba(59, 91, 219, 0.35)", flash: true }] : []),
  ];

  return (
    <div className="flex flex-col h-[calc(100vh-3rem)]">
      <div className="flex items-center gap-3 whitespace-nowrap border-b border-border bg-surface px-4 h-11 shrink-0 overflow-x-auto">
        <Link href="/" className="text-muted hover:text-foreground">←</Link>
        <b className="truncate">{doc.title}</b>
        <span className="flex-1" />
        <button onClick={() => go(page - 1)} disabled={page <= 1} className="px-2 disabled:opacity-30">◀</button>
        <input value={page} onChange={(e) => go(Number(e.target.value) || 1)}
               className="w-12 rounded border border-border bg-background text-center" aria-label="Page number" />
        <span className="text-muted">/ {pages}</span>
        <button onClick={() => go(page + 1)} disabled={page >= pages} className="px-2 disabled:opacity-30">▶</button>
        <span className="text-border">|</span>
        <button onClick={() => setZoom((z) => Math.max(0.5, z - 0.1))} className="px-2" aria-label="Zoom out">−</button>
        <span className="w-10 text-center text-muted">{Math.round(zoom * 100)}%</span>
        <button onClick={() => setZoom((z) => Math.min(3, z + 0.1))} className="px-2" aria-label="Zoom in">+</button>
        <span className="text-border">|</span>
        <select value={prefs.lang} onChange={(e) => setPrefs({ ...prefs, lang: e.target.value as Prefs["lang"] })}
                className="rounded border border-border bg-background px-1 py-0.5" aria-label="Your language">
          <option value="mr">🌐 मराठी</option>
          <option value="hi">🌐 हिन्दी</option>
          <option value="none">🌐 English only</option>
        </select>
        <select value={prefs.style} onChange={(e) => setPrefs({ ...prefs, style: e.target.value as Prefs["style"] })}
                className="rounded border border-border bg-background px-1 py-0.5" aria-label="Explanation style">
          <option value="VERY_SIMPLE">Very simple</option>
          <option value="SIMPLE">Simple</option>
          <option value="DETAILED">Detailed</option>
        </select>
        <label className="flex items-center gap-1 whitespace-nowrap text-muted" title="Explain each page automatically after 1 second">
          <input type="checkbox" checked={prefs.auto} onChange={(e) => setPrefs({ ...prefs, auto: e.target.checked })} />
          Auto
        </label>
      </div>

      <div className="grid flex-1 min-h-0 grid-cols-1 grid-rows-[1fr_50vh] md:grid-rows-1 md:grid-cols-[58fr_42fr]">
        <div ref={pane} onMouseUp={() => setTimeout(onSelect, 0)} onScroll={() => setMenu(null)}
             className="overflow-auto p-4 md:border-r border-border">
          <PdfView url={api.fileUrl(docId)} page={page} width={Math.max(200, (paneWidth - 32) * zoom)} overlays={overlays} />
        </div>

        <aside className="flex min-h-0 flex-col bg-surface max-md:rounded-t-2xl max-md:border-t max-md:border-border max-md:shadow-[0_-8px_24px_rgba(0,0,0,0.15)]">
          <div role="tablist" className="flex border-b border-border">
            {(Object.keys(TAB_LABEL) as Tab[]).map((t) => (
              <button key={t} role="tab" aria-selected={tab === t} onClick={() => setTab(t)}
                      className={`flex-1 whitespace-nowrap border-b-2 px-1 py-2.5 ${tab === t ? (t === "beyond" ? "border-beyond" : "border-pdf") + " font-semibold" : "border-transparent text-muted"}`}>
                {TAB_LABEL[t]}{t === "notes" && notes.length + highlights.length ? ` ${notes.length + highlights.length}` : ""}
              </button>
            ))}
          </div>
          <div className="flex-1 overflow-auto p-4">
            {tab === "understand" && (
              <UnderstandTab key={ocrNonce} docId={docId} page={page} prefs={prefs} selection={selection} selectionStream={selectionStream}
                             onOcr={() => setOcrNonce((n) => n + 1)}
                             onCloseSelection={() => { selectionStream.reset(); setSelection(null); }} onSaveNote={saveAiNote} />
            )}
            {tab === "deepen" && (
              <DeepenTab docId={docId} page={page} target={deepen} onSelect={setDeepen}
                         onSaveNote={(p, content) => saveAiNote(p, content)} />
            )}
            {tab === "beyond" && <BeyondTab docId={docId} target={deepen} onSaveNote={(p, content) => saveAiNote(p, content)} />}
            {tab === "ask" && (
              <AskTab docId={docId} page={page} prefs={prefs} quote={quote} onClearQuote={() => setQuote(null)}
                      onPage={(p) => go(p)} onSaveNote={saveAiNote} />
            )}
            {tab === "notes" && (
              <NotesTab notes={notes} highlights={highlights} editingId={editingId} onJump={jump}
                        onUpdate={updateNote}
                        onDelete={(n) => api.deleteNote(n.id).then(() => setNotes((all) => all.filter((x) => x.id !== n.id)))}
                        onDeleteHighlight={(h) => api.deleteHighlight(h.id).then(() => setHighlights((all) => all.filter((x) => x.id !== h.id)))} />
            )}
          </div>
        </aside>
      </div>

      {menu && (
        <div role="menu" style={{ left: menu.x, top: Math.max(8, menu.y - 44) }}
             onMouseDown={(e) => e.preventDefault() /* keep the text selected */}
             className="fixed z-10 flex max-w-[calc(100vw-16px)] -translate-x-1/2 flex-wrap items-center gap-0.5 rounded-xl border border-border bg-surface p-1 shadow-lg max-md:!left-1/2 max-md:!top-auto max-md:bottom-3">
          <button onClick={() => runSelection("EXPLAIN")} className="rounded-md px-2 py-1 hover:bg-background">💬 Explain</button>
          {prefs.lang !== "none" && (
            <button onClick={() => runSelection("TRANSLATE")} className="rounded-md px-2 py-1 hover:bg-background">
              🌐 {prefs.lang === "mr" ? "मराठीत" : "हिन्दी में"}
            </button>
          )}
          <button onClick={() => deepenSelection("deepen")} disabled={menu.text.length > 80} title={menu.text.length > 80 ? "Select a single term to deepen" : ""}
                  className="rounded-md px-2 py-1 hover:bg-background disabled:opacity-40">🔬 Deepen</button>
          <button onClick={() => deepenSelection("beyond")} disabled={menu.text.length > 80} title={menu.text.length > 80 ? "Select a single term" : ""}
                  className="rounded-md px-2 py-1 hover:bg-background disabled:opacity-40">🧭 Beyond</button>
          <button onClick={askAboutSelection} className="rounded-md px-2 py-1 hover:bg-background">❓ Ask</button>
          <button onClick={noteFromSelection} className="rounded-md px-2 py-1 hover:bg-background">📝 Note</button>
          {COLORS.map((c) => (
            <button key={c} onClick={() => highlightSelection(c)} aria-label={`Highlight ${c}`}
                    className="mx-0.5 h-4 w-4 rounded-full border border-black/10" style={{ background: HIGHLIGHT_CSS[c] }} />
          ))}
        </div>
      )}

      {toast && (
        <div className="fixed bottom-4 left-1/2 z-20 -translate-x-1/2 rounded-lg bg-foreground px-4 py-2 text-background">{toast}</div>
      )}
    </div>
  );
}
