"use client";

import Link from "next/link";
import { useEffect, useState } from "react";
import { api, type AttemptResult, type Flashcard, type Question } from "@/lib/api";
import { notifyUsage } from "@/app/usage-meter";

/**
 * A practice session: questions for the given concepts (or your weakest ones), then due flashcards.
 * Open answers are graded by the AI; multiple choice is checked instantly.
 */
export default function Practice({ documentId, conceptIds }: { documentId?: string; conceptIds: number[] }) {
  const [mode, setMode] = useState<"questions" | "cards">("questions");
  const [questions, setQuestions] = useState<Question[] | null>(null);
  const [error, setError] = useState("");
  const [index, setIndex] = useState(0);
  const [results, setResults] = useState<AttemptResult[]>([]);
  const [cards, setCards] = useState<Flashcard[] | null>(null);
  const key = conceptIds.join(",");

  useEffect(() => {
    const req = conceptIds.length ? { documentId, conceptIds } : { documentId, weakest: true };
    // Cards load after the set: preparing a set creates flashcards for concepts practised the first time.
    const loadCards = () => api.dueCards(documentId).then(setCards, () => setCards([]));
    // Deferred + cancellable so React's dev double-mount sends one (paid) request, not two.
    const t = setTimeout(() => api.practiceSet(req).then(
      (q) => { setQuestions(q); notifyUsage(); loadCards(); },
      (e: Error) => { setError(e.message); loadCards(); }), 0);
    return () => clearTimeout(t);
    // eslint-disable-next-line react-hooks/exhaustive-deps -- `key` stands for conceptIds
  }, [documentId, key]);

  return (
    <main className="mx-auto w-full max-w-2xl px-4 py-6">
      <div className="mb-4 flex items-center gap-3">
        <h1 className="text-2xl font-semibold">🎯 Practice</h1>
        <span className="flex-1" />
        <div className="inline-flex rounded-lg bg-surface p-1">
          {(["questions", "cards"] as const).map((m) => (
            <button key={m} onClick={() => setMode(m)} className={`rounded-md px-3 py-1 ${mode === m ? "bg-background shadow-sm" : "text-muted"}`}>
              {m === "questions" ? "Questions" : `Flashcards${cards?.length ? ` (${cards.length} due)` : ""}`}
            </button>
          ))}
        </div>
      </div>

      {mode === "cards" ? (
        <Cards cards={cards} onDone={(id) => setCards((c) => c?.filter((x) => x.id !== id) ?? null)} />
      ) : error ? (
        <p className="rounded-xl border border-border bg-surface p-4">{error} <Link href="/" className="text-pdf">Go to library →</Link></p>
      ) : !questions ? (
        <p className="text-muted">Preparing questions… (the first time for a concept takes a few seconds)</p>
      ) : index >= questions.length ? (
        <Summary results={results} onAgain={() => location.reload()} />
      ) : (
        <QuestionCard key={questions[index].id} q={questions[index]} n={index + 1} total={questions.length}
                      onAnswered={(r) => setResults((all) => [...all, r])} onNext={() => setIndex((i) => i + 1)} />
      )}
    </main>
  );
}

function QuestionCard({ q, n, total, onAnswered, onNext }: {
  q: Question; n: number; total: number; onAnswered: (r: AttemptResult) => void; onNext: () => void;
}) {
  const [answer, setAnswer] = useState("");
  const [picked, setPicked] = useState<number | null>(null);
  const [result, setResult] = useState<AttemptResult | null>(null);
  const [grading, setGrading] = useState(false);
  const [error, setError] = useState("");

  async function submit(value: string) {
    setGrading(true);
    setError("");
    try {
      const r = await api.attempt(q.id, value);
      setResult(r);
      onAnswered(r);
      notifyUsage();
    } catch (e) {
      setError((e as Error).message);
    }
    setGrading(false);
  }

  const label = { MCQ: "Multiple choice", EXPLAIN: "Explain it back", INTERVIEW: "Interview question" }[q.type];

  return (
    <div className="rounded-xl border border-border bg-surface p-5">
      <div className="mb-2 text-xs text-muted">{n} / {total} · {label} · {q.conceptName}</div>
      <p className="mb-4 text-base font-medium">{q.prompt}</p>

      {q.type === "MCQ" ? (
        <div className="space-y-2">
          {q.options.map((o, i) => {
            const right = result && i === result.answerIndex;
            const wrong = result && i === picked && i !== result.answerIndex;
            return (
              <button key={i} disabled={!!result || grading} onClick={() => { setPicked(i); submit(String(i)); }}
                      className={`block w-full rounded-lg border px-3 py-2 text-left ${right ? "border-green-600 bg-green-600/10" : wrong ? "border-red-600 bg-red-600/10" : "border-border hover:border-pdf"}`}>
                {o}
              </button>
            );
          })}
        </div>
      ) : (
        <>
          <textarea value={answer} onChange={(e) => setAnswer(e.target.value)} disabled={!!result} rows={6} maxLength={4000}
                    placeholder="Answer in your own words…" className="w-full rounded-lg border border-border bg-background p-3" />
          {!result && (
            <button onClick={() => submit(answer)} disabled={grading || !answer.trim()}
                    className="mt-2 rounded-lg bg-pdf px-4 py-1.5 text-white disabled:opacity-40">
              {grading ? "Grading…" : "Submit for grading"}
            </button>
          )}
        </>
      )}

      {error && <p className="mt-3 text-red-600">{error}</p>}

      {result && (
        <div className="mt-4 space-y-2 border-t border-border pt-4">
          <p className="text-base font-semibold">
            {result.score >= 70 ? "✅" : result.score >= 40 ? "🟡" : "❌"} {q.type === "MCQ" ? (result.score === 100 ? "Correct" : "Not quite") : `Score ${result.score}/100`}
          </p>
          {result.feedback && <p>{result.feedback}</p>}
          {result.missing.length > 0 && (
            <div><span className="text-muted">Missing: </span>{result.missing.join(" · ")}</div>
          )}
          <details className="text-muted" open={q.type === "MCQ"}>
            <summary className="cursor-pointer">Why</summary>
            {q.type !== "MCQ" && <p className="mt-1"><b>Strong answer:</b> {result.modelAnswer}</p>}
            <p className="mt-1">{result.explanation}</p>
          </details>
          <p className="text-sm">
            {result.after.name}: mastery {result.before.score} → <b>{result.after.score}</b>
            <span className="ml-2 rounded-full bg-background px-2 text-xs">{result.after.stage.toLowerCase()}</span>
          </p>
          <button onClick={onNext} className="rounded-lg bg-pdf px-4 py-1.5 text-white">Next →</button>
        </div>
      )}
    </div>
  );
}

function Summary({ results, onAgain }: { results: AttemptResult[]; onAgain: () => void }) {
  const avg = results.length ? Math.round(results.reduce((s, r) => s + r.score, 0) / results.length) : 0;
  const latest = new Map(results.map((r) => [r.after.id, r]));
  return (
    <div className="rounded-xl border border-border bg-surface p-5">
      <h2 className="mb-2 text-xl font-semibold">Session done: average {avg}/100</h2>
      <ul className="mb-4 space-y-1">
        {[...latest.values()].map((r) => (
          <li key={r.after.id}>{r.after.name}: <b>{r.after.score}</b> <span className="text-xs text-muted">({r.after.stage.toLowerCase()})</span></li>
        ))}
      </ul>
      <div className="flex gap-3">
        <button onClick={onAgain} className="rounded-lg bg-pdf px-4 py-1.5 text-white">Practise again</button>
        <Link href="/mastery" className="rounded-lg border border-border px-4 py-1.5">See mastery →</Link>
      </div>
    </div>
  );
}

function Cards({ cards, onDone }: { cards: Flashcard[] | null; onDone: (id: number) => void }) {
  const [flipped, setFlipped] = useState(false);
  if (!cards) return <p className="text-muted">Loading flashcards…</p>;
  if (cards.length === 0) return <p className="rounded-xl border border-border bg-surface p-4">No flashcards due. 🎉 Practise concepts to create more.</p>;
  const card = cards[0];

  async function answer(gotIt: boolean) {
    await api.review(card.id, gotIt);
    setFlipped(false);
    onDone(card.id);
  }

  return (
    <div>
      <button onClick={() => setFlipped(!flipped)}
              className="flex min-h-56 w-full flex-col items-center justify-center rounded-xl border border-border bg-surface p-6 text-center">
        {flipped ? <p className="text-base">{card.back}</p> : <p className="text-2xl font-semibold">{card.front}</p>}
        <span className="mt-4 text-xs text-muted">{flipped ? card.conceptName : "tap to flip"}</span>
      </button>
      <div className="mt-3 flex justify-center gap-3">
        <button onClick={() => answer(false)} className="rounded-lg border border-border px-4 py-1.5">Again</button>
        <button onClick={() => answer(true)} className="rounded-lg bg-pdf px-4 py-1.5 text-white">Got it ✓</button>
      </div>
      <p className="mt-2 text-center text-xs text-muted">{cards.length} due</p>
    </div>
  );
}
