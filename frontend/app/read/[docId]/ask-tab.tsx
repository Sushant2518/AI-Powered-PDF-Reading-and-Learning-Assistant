"use client";

import { useEffect, useRef, useState } from "react";
import { api, type ChatMessage, type Source } from "@/lib/api";
import { parseAnswer } from "@/lib/sections";
import { useAiStream } from "@/lib/sse";
import { notifyUsage } from "@/app/usage-meter";
import AiMarkdown from "@/app/markdown";
import { BeyondBlock, beyondAsNote } from "./beyond";
import { SaveNoteButton } from "./notes-tab";
import type { Prefs } from "@/lib/prefs";

type Props = {
  docId: string;
  page: number;
  prefs: Prefs;
  quote: string | null;
  onClearQuote: () => void;
  onPage: (page: number) => void;
  onSaveNote: (page: number, content: string, selectedText?: string) => Promise<unknown>;
};

type Beyond = { content?: string; sources?: Source[]; hidden?: number; error?: string };

export default function AskTab({ docId, page, prefs, quote, onClearQuote, onPage, onSaveNote }: Props) {
  const [history, setHistory] = useState<ChatMessage[] | null>(null);
  const [pending, setPending] = useState<{ question: string; quote: string | null; page: number } | null>(null);
  const [input, setInput] = useState("");
  const [allowBeyond, setAllowBeyond] = useState(true);
  const stream = useAiStream(notifyUsage);
  const log = useRef<HTMLDivElement>(null);

  const reload = () => api.chat(docId).then(setHistory, () => setHistory([]));
  useEffect(() => { api.chat(docId).then(setHistory, () => setHistory([])); }, [docId]);

  // When an answer finishes, reload the saved conversation (it now contains the answer).
  const { status, reset } = stream;
  useEffect(() => {
    if (status !== "done") return;
    api.chat(docId).then((h) => { setHistory(h); setPending(null); reset(); }, () => {});
  }, [status, docId, reset]);

  useEffect(() => { log.current?.scrollTo({ top: log.current.scrollHeight }); }, [history, stream.text, stream.events]);

  function ask(question: string) {
    const q = question.trim();
    if (!q || stream.status === "streaming") return;
    setPending({ question: q, quote, page });
    setInput("");
    onClearQuote();
    stream.start(api.askUrl(docId), {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ question: q, page, quotedText: quote, lang: prefs.lang, allowBeyond }),
    });
  }

  const live = parseAnswer(stream.text);
  const liveBeyond = stream.events.beyond as Beyond | undefined;
  const searching = stream.events.beyond_searching as { query: string } | undefined;
  const lastFollowUps = history?.findLast((m) => m.role === "ASSISTANT")?.followUps ?? [];

  return (
    <div className="flex h-full flex-col">
      <div ref={log} className="flex-1 space-y-3 overflow-auto">
        {history === null ? <p className="text-muted">Loading conversation…</p>
          : history.length === 0 && !pending ? (
            <p className="text-muted">Ask anything about this page. The answer comes from your PDF first (with page numbers); 🌐 Beyond adds sourced extras when the book doesn&apos;t cover it.</p>
          ) : null}

        {history?.map((m) => m.role === "USER" ? (
          <Question key={m.id} text={m.content} quote={m.quotedText} />
        ) : (
          <div key={m.id} className="space-y-2">
            <AnswerBlock raw={m.content} onPage={onPage} />
            {m.beyond && m.sources && <BeyondBlock content={m.beyond} sources={m.sources} hidden={0} />}
            <SaveNoteButton onSave={() => onSaveNote(m.pageNumber, m.content + (m.beyond && m.sources ? "\n\n" + beyondAsNote("Beyond the PDF", m.beyond, m.sources) : ""))} />
          </div>
        ))}

        {pending && (
          <div className="space-y-2">
            <Question text={pending.question} quote={pending.quote} />
            {stream.status === "error" ? <p className="text-red-600">{stream.error}</p>
              : !stream.text ? <p className="text-muted">Reading pages {Math.max(1, pending.page - 1)}–{pending.page + 1}…</p>
              : <AnswerBlock raw={stream.text} streaming={!live.beyondQuery && !stream.text.includes("<<<FOLLOWUPS>>>")} onPage={onPage} />}
            {searching && !liveBeyond && <p className="text-beyond">🔎 Searching trusted sources: “{searching.query}”…</p>}
            {liveBeyond?.error && <p className="text-red-600">🌐 {liveBeyond.error}</p>}
            {liveBeyond?.content && <BeyondBlock content={liveBeyond.content} sources={liveBeyond.sources ?? []} hidden={liveBeyond.hidden ?? 0} />}
          </div>
        )}

        {!pending && lastFollowUps.length > 0 && (
          <div className="flex flex-wrap gap-1.5">
            {lastFollowUps.map((f) => (
              <button key={f} onClick={() => ask(f)} className="rounded-full border border-border px-2.5 py-0.5 text-left hover:border-pdf">› {f}</button>
            ))}
          </div>
        )}
      </div>

      <div className="mt-3 border-t border-border pt-3">
        {quote && (
          <div className="mb-2 flex items-start gap-2 rounded border-l-2 border-muted pl-2 text-xs text-muted">
            <span className="flex-1 italic line-clamp-2">❝{quote}❞</span>
            <button onClick={onClearQuote} aria-label="Remove quote">✕</button>
          </div>
        )}
        <form onSubmit={(e) => { e.preventDefault(); ask(input); }} className="flex gap-2">
          <input value={input} onChange={(e) => setInput(e.target.value)} placeholder={`Ask about p.${page}…`} maxLength={2000}
                 className="flex-1 rounded-lg border border-border bg-background px-3 py-2" />
          <button disabled={!input.trim() || stream.status === "streaming"} className="rounded-lg bg-pdf px-3 text-white disabled:opacity-40">➤</button>
        </form>
        <div className="mt-2 flex items-center gap-4 text-xs text-muted">
          <label className="flex items-center gap-1">
            <input type="checkbox" checked={allowBeyond} onChange={(e) => setAllowBeyond(e.target.checked)} /> Allow 🌐 Beyond (web search)
          </label>
          <span className="flex-1" />
          {(history?.length ?? 0) > 0 && (
            <button onClick={() => confirm("Clear this conversation?") && api.clearChat(docId).then(reload)} className="hover:text-red-600">Clear chat</button>
          )}
        </div>
      </div>
    </div>
  );
}

function Question({ text, quote }: { text: string; quote: string | null }) {
  return (
    <div className="ml-auto max-w-[90%] rounded-xl bg-background px-3 py-2">
      {quote && <p className="mb-1 border-l-2 border-muted pl-2 text-xs italic text-muted line-clamp-2">❝{quote}❞</p>}
      {text}
    </div>
  );
}

function AnswerBlock({ raw, streaming = false, onPage }: { raw: string; streaming?: boolean; onPage: (p: number) => void }) {
  const { text, native } = parseAnswer(raw);
  return (
    <div className="rounded-xl border border-border px-3 py-2">
      <div className="mb-1 text-xs font-semibold uppercase tracking-wider text-pdf">📄 From your PDF</div>
      <AiMarkdown text={text} streaming={streaming && !native} onPage={onPage} />
      {native && <AiMarkdown text={native} streaming={streaming} onPage={onPage} className="native mt-2 border-t border-border pt-2" />}
    </div>
  );
}
