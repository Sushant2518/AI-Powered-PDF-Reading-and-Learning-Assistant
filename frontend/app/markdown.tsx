"use client";

import { useEffect, useId, useState } from "react";
import Markdown from "react-markdown";
import { linkMarkers } from "@/lib/sections";
import type { Source } from "@/lib/api";
import { isDark } from "@/lib/prefs";

type Props = {
  text: string;
  streaming?: boolean;
  className?: string;
  /** Makes [p.N] markers buttons that jump to that page. */
  onPage?: (page: number) => void;
  /** Makes [n] markers links to these sources. */
  sources?: Source[];
};

/**
 * Renders AI markdown. ```mermaid blocks become diagrams once the text has finished streaming;
 * [p.N] markers become page buttons and [n] markers source links.
 */
export default function AiMarkdown({ text, streaming = false, className = "", onPage, sources }: Props) {
  return (
    <div className={`md ${className} ${streaming ? "caret" : ""}`}>
      <Markdown
        components={{
          a({ href = "", children }) {
            const page = href.match(/^#page-(\d+)$/);
            if (page) {
              return (
                <button onClick={() => onPage?.(+page[1])}
                        className="mx-0.5 rounded-full bg-background px-1.5 text-xs text-pdf hover:underline">{children}</button>
              );
            }
            // AI-produced links: open in a new tab, never send our page as referrer.
            return <a href={href} target="_blank" rel="noopener noreferrer" className="text-beyond underline">{children}</a>;
          },
          code({ className: lang, children }) {
            const code = String(children).replace(/\n$/, "");
            if (lang === "language-mermaid" && !streaming) return <Mermaid code={code} />;
            return <code className={lang}>{children}</code>;
          },
        }}
      >
        {linkMarkers(text, sources)}
      </Markdown>
    </div>
  );
}

function Mermaid({ code }: { code: string }) {
  const id = "m" + useId().replace(/[^a-zA-Z0-9]/g, "");
  const [svg, setSvg] = useState<string | null>(null);
  const [failed, setFailed] = useState(false);

  useEffect(() => {
    let cancelled = false;
    import("mermaid").then(async ({ default: mermaid }) => {
      const dark = isDark();
      // strict = no scripts or click handlers in diagrams (the text comes from an AI reading a PDF).
      mermaid.initialize({ startOnLoad: false, securityLevel: "strict", theme: dark ? "dark" : "default" });
      try {
        const { svg } = await mermaid.render(id, code);
        if (!cancelled) setSvg(svg);
      } catch {
        if (!cancelled) setFailed(true);
      }
    });
    return () => { cancelled = true; };
  }, [id, code]);

  if (failed) return <code className="block whitespace-pre">{code}</code>;
  if (!svg) return <span className="text-muted">Drawing diagram…</span>;
  return <span className="block overflow-auto" dangerouslySetInnerHTML={{ __html: svg }} />;
}
