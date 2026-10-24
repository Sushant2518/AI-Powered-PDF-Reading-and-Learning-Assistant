export type Term = { term: string; meaning: string };

/**
 * Splits an Ask answer: answer [--- native] <<<BEYOND>>> query <<<FOLLOWUPS>>> questions.
 * Works on partial (still streaming) text too.
 */
export function parseAnswer(raw: string): { text: string; native: string; beyondQuery: string; followUps: string[] } {
  const hidePartialMarker = (s: string) => s.replace(/<{1,3}[A-Z]*>{0,2}$/, "").trim();
  const [beforeFollowUps, followPart = ""] = raw.split("<<<FOLLOWUPS>>>");
  const [answer, beyondPart = ""] = beforeFollowUps.split("<<<BEYOND>>>");
  const [text, native = ""] = answer.split(/\n---\n/);
  return {
    text: hidePartialMarker(text),
    native: hidePartialMarker(native),
    beyondQuery: beyondPart.trim().split("\n")[0]?.trim() ?? "",
    followUps: followPart.split("\n").map((l) => l.replace(/^\s*([-*]|\d+[.)])\s*/, "").trim()).filter(Boolean).slice(0, 3),
  };
}

/** Makes [p.12] page markers and [n] source markers clickable markdown links. */
export function linkMarkers(md: string, sources: { n: number; url: string }[] = []): string {
  const byN = new Map(sources.map((s) => [s.n, s.url]));
  return md
    .replace(/\[p\.(\d+)\]/g, "[p.$1](#page-$1)")
    .replace(/\[(\d+)\](?![(\]])/g, (m, n) => (byN.has(+n) ? `[[${n}]](${byN.get(+n)})` : m));
}

/**
 * Splits the page explanation into its parts. The model writes:
 *   simple English … <<<NATIVE>>> native … <<<TERMS>>> term :: meaning (one per line)
 * Works on partial (still streaming) text too.
 */
export function parseSections(raw: string): { simple: string; native: string; terms: Term[] } {
  const hidePartialMarker = (s: string) => s.replace(/<{1,3}[A-Z]*>{0,2}$/, "").trim();
  const [beforeTerms, termsPart = ""] = raw.split("<<<TERMS>>>");
  const [simple, native = ""] = beforeTerms.split("<<<NATIVE>>>");
  const terms = termsPart
    .split("\n")
    .map((line) => line.split("::"))
    .filter((parts) => parts.length >= 2 && parts[0].trim())
    .map(([term, ...meaning]) => ({
      term: term.replace(/^[\s*\-\d.]+/, "").replace(/\*\*/g, "").trim(),
      meaning: meaning.join("::").trim(),
    }));
  return { simple: hidePartialMarker(simple), native: hidePartialMarker(native), terms };
}
