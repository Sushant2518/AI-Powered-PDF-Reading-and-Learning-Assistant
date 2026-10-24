"use client";

import { useEffect, useRef, useState } from "react";
import type { Highlight, Note, Rect } from "@/lib/api";
import AiMarkdown from "@/app/markdown";

export const HIGHLIGHT_CSS = {
  yellow: "rgba(255, 214, 102, 0.55)",
  green: "rgba(140, 220, 120, 0.5)",
  blue: "rgba(120, 190, 255, 0.5)",
  pink: "rgba(255, 150, 190, 0.5)",
} as const;

type Props = {
  notes: Note[];
  highlights: Highlight[];
  editingId: number | null;
  onUpdate: (note: Note, content: string) => void;
  onDelete: (note: Note) => void;
  onDeleteHighlight: (h: Highlight) => void;
  onJump: (page: number, rects?: Rect[]) => void;
};

export default function NotesTab({ notes, highlights, editingId, onUpdate, onDelete, onDeleteHighlight, onJump }: Props) {
  const pages = [...new Set([...notes.map((n) => n.pageNumber), ...highlights.map((h) => h.pageNumber)])].sort((a, b) => a - b);

  if (pages.length === 0) {
    return <p className="text-muted">No notes yet. Select text on the page and choose 📝 Note, or save any AI explanation.</p>;
  }

  return (
    <div className="space-y-5">
      {pages.map((p) => (
        <section key={p}>
          <button onClick={() => onJump(p)} className="mb-2 text-xs font-semibold uppercase tracking-wider text-muted hover:text-pdf">
            Page {p}
          </button>
          {notes.filter((n) => n.pageNumber === p).map((n) => (
            <NoteCard key={n.id} note={n} autoFocus={n.id === editingId} onUpdate={onUpdate} onDelete={onDelete} onJump={onJump} />
          ))}
          {highlights.filter((h) => h.pageNumber === p).map((h) => (
            <div key={h.id} className="mb-2 flex items-start gap-2 rounded-lg border border-border bg-background p-2">
              <span className="mt-1 h-3 w-3 shrink-0 rounded-full" style={{ background: HIGHLIGHT_CSS[h.color] }} />
              <span className="flex-1 italic line-clamp-3">“{h.selectedText}”</span>
              <button onClick={() => onJump(h.pageNumber, h.anchor.rects)} className="text-xs text-muted hover:text-pdf">↗</button>
              <button onClick={() => onDeleteHighlight(h)} className="text-xs text-muted hover:text-red-600" aria-label="Remove highlight">✕</button>
            </div>
          ))}
        </section>
      ))}
    </div>
  );
}

function NoteCard({ note, autoFocus, onUpdate, onDelete, onJump }: {
  note: Note; autoFocus: boolean;
  onUpdate: Props["onUpdate"]; onDelete: Props["onDelete"]; onJump: Props["onJump"];
}) {
  const box = useRef<HTMLTextAreaElement>(null);
  const [draft, setDraft] = useState(note.content);
  useEffect(() => { if (autoFocus) box.current?.focus(); }, [autoFocus]);

  // Autosave shortly after typing stops, so nothing is lost if the tab closes before the box loses focus.
  useEffect(() => {
    if (draft === note.content) return;
    const t = setTimeout(() => onUpdate(note, draft), 800);
    return () => clearTimeout(t);
  }, [draft, note, onUpdate]);

  return (
    <div className="mb-2 rounded-lg border border-border bg-background p-3">
      {note.selectedText && (
        <p className="mb-2 border-l-4 border-amber-400 pl-2 italic line-clamp-4">“{note.selectedText}”</p>
      )}
      {note.source === "AI" ? (
        <AiMarkdown text={note.content} />
      ) : (
        <textarea ref={box} value={draft} onChange={(e) => setDraft(e.target.value)} placeholder="Write your note…" rows={3}
                  onBlur={() => draft !== note.content && onUpdate(note, draft)}
                  className="w-full resize-y rounded border border-border bg-surface p-2" />
      )}
      <div className="mt-1 flex gap-3 text-xs text-muted">
        <span>{note.source === "AI" ? "🤖 AI" : "✎ Me"} · p.{note.pageNumber}</span>
        <span className="flex-1" />
        <button onClick={() => onJump(note.pageNumber, note.anchor?.rects)} className="hover:text-pdf">↗ Jump</button>
        <button onClick={() => confirm("Delete this note?") && onDelete(note)} className="hover:text-red-600">Delete</button>
      </div>
    </div>
  );
}

/** "Save to notes" button that confirms once saved. */
export function SaveNoteButton({ onSave }: { onSave: () => Promise<unknown> }) {
  const [state, setState] = useState<"idle" | "saving" | "saved">("idle");
  return (
    <button disabled={state !== "idle"} className="text-xs text-muted hover:text-foreground disabled:opacity-70"
            onClick={() => { setState("saving"); onSave().then(() => setState("saved"), () => setState("idle")); }}>
      {state === "saved" ? "✓ Saved to notes" : "📝 Save to notes"}
    </button>
  );
}
