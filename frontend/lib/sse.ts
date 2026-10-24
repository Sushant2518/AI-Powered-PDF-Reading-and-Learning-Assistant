"use client";

import { useCallback, useEffect, useRef, useState } from "react";

type Status = "idle" | "streaming" | "done" | "error";

/** Reads a server-sent-events response (works with POST, unlike EventSource). */
// eslint-disable-next-line @typescript-eslint/no-explicit-any
async function readSse(res: Response, onEvent: (name: string, data: Record<string, any>) => void) {
  if (!res.ok || !res.body) {
    const body = await res.json().catch(() => ({}));
    onEvent("error", { message: body.message ?? body.error ?? `Request failed (${res.status})` });
    return;
  }
  const reader = res.body.pipeThrough(new TextDecoderStream()).getReader();
  let buffer = "";
  for (;;) {
    const { value, done } = await reader.read();
    if (done) break;
    buffer += value;
    let end;
    while ((end = buffer.indexOf("\n\n")) >= 0) {
      const block = buffer.slice(0, end);
      buffer = buffer.slice(end + 2);
      let name = "message";
      const data: string[] = [];
      for (const line of block.split("\n")) {
        if (line.startsWith("event:")) name = line.slice(6).trim();
        else if (line.startsWith("data:")) data.push(line.slice(5));
      }
      if (data.length) onEvent(name, JSON.parse(data.join("\n")));
    }
  }
}

/** Streams AI text from an SSE endpoint. Starting a new request cancels the previous one. */
export function useAiStream(onDone?: () => void) {
  const [text, setText] = useState("");
  const [status, setStatus] = useState<Status>("idle");
  const [error, setError] = useState("");
  // Named events other than delta/done/error (e.g. "beyond"), last payload per name.
  const [events, setEvents] = useState<Record<string, Record<string, unknown>>>({});
  const abort = useRef<AbortController | null>(null);

  const start = useCallback((url: string, init?: RequestInit) => {
    abort.current?.abort();
    const ctrl = (abort.current = new AbortController());
    setText("");
    setError("");
    setEvents({});
    setStatus("streaming");
    fetch(url, { ...init, signal: ctrl.signal })
      .then((res) =>
        readSse(res, (name, data) => {
          if (ctrl.signal.aborted) return;
          if (name === "delta") setText((t) => t + data.text);
          else if (name === "done") { setStatus("done"); onDone?.(); }
          else if (name === "error") { setError(String(data.message)); setStatus("error"); onDone?.(); }
          else setEvents((e) => ({ ...e, [name]: data }));
        }))
      .catch((e) => {
        if (ctrl.signal.aborted) return;
        setError(e instanceof Error ? e.message : String(e));
        setStatus("error");
      });
  }, [onDone]);

  const reset = useCallback(() => {
    abort.current?.abort();
    setText("");
    setEvents({});
    setStatus("idle");
  }, []);

  useEffect(() => () => abort.current?.abort(), []);

  return { text, status, error, events, start, reset };
}
