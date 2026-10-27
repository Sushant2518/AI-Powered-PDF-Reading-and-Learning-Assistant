export const API = process.env.NEXT_PUBLIC_API_URL ?? "http://localhost:8080/api";

export type Doc = {
  id: string;
  title: string;
  sizeBytes: number;
  pageCount: number;
  status: "PROCESSING" | "READY" | "FAILED";
  lastPage: number;
  createdAt: string;
};

export type PageText = { pageNumber: number; text: string; needsOcr: boolean };

export const DOMAIN_LABEL: Record<string, string> = {
  SOFTWARE_ENGINEERING: "Software Eng.", BACKEND: "Backend", FRONTEND: "Frontend",
  DISTRIBUTED_SYSTEMS: "Distributed", SECURITY: "Security", AI_ML: "AI / ML",
  DEVOPS_CLOUD: "DevOps & Cloud", DATA_DATABASES: "Data", CS_FUNDAMENTALS: "CS Fundamentals", UNSORTED: "Unsorted",
};

export type Concept = { id: number; name: string; domain: string; definition: string; deepened: boolean };

/** Rectangles normalised 0..1 relative to the rendered page, so they survive zoom changes. */
export type Rect = { x: number; y: number; w: number; h: number };
export type Anchor = { rects: Rect[] };

export type Note = {
  id: number;
  pageNumber: number;
  selectedText: string | null;
  anchor: Anchor | null;
  content: string;
  source: "ME" | "AI";
  createdAt: string;
};

export type Provider = "gemini" | "claude" | "local";
export type Settings = {
  provider: Provider; localModel: string; monthlyBudgetUsd: number; claudeModel: string; geminiModel: string; trustedDomains: string[];
};
export type Usage = { provider: Provider; localModel: string; spentUsd: number; budgetUsd: number; geminiRequestsToday: number };

export type Source = { n: number; url: string; title: string };
export type Beyond = { content: string; sources: Source[]; hidden: number; accessedAt: string };

export type ChatMessage = {
  id: number;
  role: "USER" | "ASSISTANT";
  content: string;
  pageNumber: number;
  quotedText: string | null;
  followUps: string[] | null;
  beyond: string | null;
  sources: Source[] | null;
  createdAt: string;
};

export type Stage = "READ" | "UNDERSTOOD" | "PRACTISED" | "MASTERED";
export type ConceptMastery = { id: number; name: string; domain: string; score: number; stage: Stage; attempts: number };
export type Question = { id: number; conceptId: number; conceptName: string; type: "MCQ" | "EXPLAIN" | "INTERVIEW"; prompt: string; options: string[] };
export type AttemptResult = {
  score: number; answerIndex: number | null; modelAnswer: string; explanation: string;
  missing: string[]; feedback: string; before: ConceptMastery; after: ConceptMastery;
};
export type Flashcard = { id: number; conceptId: number; conceptName: string; front: string; back: string };
export type Dashboard = {
  overall: number; mastered: number; dueFlashcards: number;
  concepts: ConceptMastery[]; weakest: ConceptMastery[];
  domains: { domain: string; concepts: number; score: number }[];
};

export type HighlightColor = "yellow" | "green" | "blue" | "pink";
export type Highlight = { id: number; pageNumber: number; selectedText: string; anchor: Anchor; color: HighlightColor };

async function call<T>(path: string, init?: RequestInit): Promise<T> {
  const res = await fetch(`${API}${path}`, init);
  if (!res.ok) {
    const body = await res.json().catch(() => ({}));
    throw new Error(body.message ?? body.error ?? `${res.status} ${res.statusText}`);
  }
  return res.status === 204 ? (undefined as T) : res.json();
}

const json = (method: string, body: unknown): RequestInit => ({
  method,
  headers: { "Content-Type": "application/json" },
  body: JSON.stringify(body),
});

export const api = {
  list: () => call<Doc[]>("/documents"),
  get: (id: string) => call<Doc>(`/documents/${id}`),
  page: (id: string, n: number) => call<PageText>(`/documents/${id}/pages/${n}`),
  fileUrl: (id: string) => `${API}/documents/${id}/file`,
  upload: (file: File) => {
    const body = new FormData();
    body.append("file", file);
    return call<Doc>("/documents", { method: "POST", body });
  },
  patch: (id: string, patch: { title?: string; lastPage?: number }) => call<Doc>(`/documents/${id}`, json("PATCH", patch)),
  remove: (id: string) => call<void>(`/documents/${id}`, { method: "DELETE" }),

  ocr: (id: string, n: number) => call<PageText>(`/documents/${id}/pages/${n}/ocr`, { method: "POST" }),
  settings: () => call<Settings>("/settings"),
  saveSettings: (s: Pick<Settings, "provider" | "localModel" | "monthlyBudgetUsd">) => call<Settings>("/settings", json("PUT", s)),
  usage: () => call<Usage>("/usage"),

  concepts: (id: string, n: number) => call<Concept[]>(`/documents/${id}/pages/${n}/concepts`),
  createConcept: (documentId: string, page: number, name: string) =>
    call<Concept>("/concepts", json("POST", { documentId, page, name })),
  deepenUrl: (conceptId: number, documentId: string, page: number) =>
    `${API}/concepts/${conceptId}/deepen?documentId=${documentId}&page=${page}`,

  beyond: (conceptId: number, documentId: string, page: number, regenerate = false) =>
    call<Beyond>(`/concepts/${conceptId}/beyond?documentId=${documentId}&page=${page}&regenerate=${regenerate}`),

  chat: (id: string) => call<ChatMessage[]>(`/documents/${id}/chat`),
  clearChat: (id: string) => call<void>(`/documents/${id}/chat`, { method: "DELETE" }),
  askUrl: (id: string) => `${API}/documents/${id}/ask`,

  practiceSet: (req: { documentId?: string; conceptIds?: number[]; weakest?: boolean }) =>
    call<Question[]>("/practice/sets", json("POST", req)),
  attempt: (questionId: number, answer: string) => call<AttemptResult>("/practice/attempts", json("POST", { questionId, answer })),
  dueCards: (documentId?: string) => call<Flashcard[]>(`/flashcards/due${documentId ? `?documentId=${documentId}` : ""}`),
  review: (cardId: number, gotIt: boolean) => call<ConceptMastery>(`/flashcards/${cardId}/review`, json("POST", { gotIt })),
  mastery: (documentId?: string) => call<Dashboard>(`/mastery${documentId ? `?documentId=${documentId}` : ""}`),
  masteryByDocument: () => call<Record<string, number>>("/mastery/documents"),

  notes: (id: string) => call<Note[]>(`/documents/${id}/notes`),
  addNote: (id: string, note: { page: number; selectedText?: string; anchor?: Anchor; content?: string; source?: "ME" | "AI" }) =>
    call<Note>(`/documents/${id}/notes`, json("POST", note)),
  updateNote: (noteId: number, content: string) => call<Note>(`/notes/${noteId}`, json("PUT", { content })),
  deleteNote: (noteId: number) => call<void>(`/notes/${noteId}`, { method: "DELETE" }),

  highlights: (id: string) => call<Highlight[]>(`/documents/${id}/highlights`),
  addHighlight: (id: string, h: { page: number; selectedText: string; anchor: Anchor; color: HighlightColor }) =>
    call<Highlight>(`/documents/${id}/highlights`, json("POST", h)),
  deleteHighlight: (hid: number) => call<void>(`/highlights/${hid}`, { method: "DELETE" }),
};
