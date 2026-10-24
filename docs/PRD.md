# PRD — Personal Tech Mastery Companion

> Working name: **SamajhPDF** ("samajh" = understand). Rename anytime.
> Version 3.0 · Owner & only user: Sushant Narvekar · Status: Draft for review
> Related: [TRD](./TRD.md) · [UI Design](./UI-DESIGN.md)

---

## 1. Vision

> **Feed it any tech PDF — a book, paper, RFC, spec, or docs export — and it turns me into an expert on it.**
> I understand every page (in simple English, with Marathi/Hindi when I want it), go deeper with code and diagrams, learn what's beyond the PDF from exact sources, practise until I can explain and build it, and track my mastery across all of tech.

**Built for one user (me): a software engineer who wants to become an expert across:**
Software Engineering · Backend · Frontend · System Design & Distributed Systems · Cyber Security · AI/ML & LLMs · DevOps & Cloud · Data & Databases · CS Fundamentals (OS, Networks).

It's also a learning project: building it with Spring Boot + Next.js + Claude teaches backend, frontend, AI integration and security along the way.

---

## 2. Problem (mine)

- Tech knowledge is huge and scattered: books, papers, RFCs, OWASP guides, framework docs.
- Reading a hard PDF (DDIA, an RFC, a security paper, the Transformer paper) is slow; I hit unclear lines, missing background, and no examples in the stack I use.
- I read but don't **retain**: no practice, no revision, no way to see what I actually know.
- Answers from Google/ChatGPT are often unsourced or outdated, so I can't trust them for depth.
- Notes, code experiments and questions are spread across tools and lose their link to the source.

---

## 3. Core Principles

1. **The PDF is the centre.** Everything links to a page and text.
2. **Two separate knowledge zones:**

| 📄 From your PDF | 🌐 Beyond the PDF |
|---|---|
| Only from the document's text | Outside knowledge: background, current versions, real-world usage, alternatives |
| Cites **page** ("p. 42") | Cites **exact source**: title, site, URL, date accessed |
| Default | On demand ("Go beyond") |

   Preferred sources: official docs (MDN, Spring, React/Next.js, Kubernetes, AWS/GCP), standards (IETF RFCs, W3C, NIST, OWASP), papers (arXiv, ACM, USENIX), and engineering blogs of reputed companies. Excluded: content farms, SEO blogs, unsourced forum answers.
3. **Explain → Show → Do.** Tech mastery needs code and hands-on work, not just text.
4. **Mastery over reading.** A topic is done when I can explain it, answer questions on it, and build or use it.
5. **Never invent.** No source → "I don't know".

---

## 4. The Mastery Loop

```
1. UNDERSTAND ─► 2. DEEPEN ─► 3. GO BEYOND ─► 4. PRACTISE ─► 5. REVISE ─► 6. TRACK MASTERY
  simple English   concepts,     sourced          MCQs, explain-   flashcards,   score per concept,
  (+ मराठी/हिन्दी    why, code      docs/RFCs/       it-back, design  spaced        knowledge map
   on demand)      examples,      papers,          questions,       repetition    across all tech
                   diagrams,      latest           coding tasks                   domains, gaps,
                   trade-offs     versions                                        "learn next"
     ▲                                                                                 │
     └──────────────────────────── weak concepts sent back ────────────────────────────┘
```

---

## 5. Release Scope

### MVP — "Master one PDF properly"
| # | Feature | Step | Priority |
|---|---|---|---|
| F1 | Single-user app; one login only if deployed online (no sign-up, no multi-user) | — | P0 |
| F2 | Upload PDF (books, papers, RFCs, docs); library with domain tags | — | P0 |
| F3 | Reader: PDF left, learning panel right | — | P0 |
| F4 | **Understand**: Simple English · मराठी/हिन्दी (toggle) · Original | 1 | P0 |
| F5 | Selection menu: Explain · Go deeper · Show code · Go beyond · Ask · Add to notes · Make flashcard | 1–3 | P0 |
| F6 | **Deepen**: concepts on the page → definition, why it matters, **code example in my stack** (Java/Spring, TypeScript/Next.js, Python for AI), **diagram** (Mermaid), trade-offs, common mistakes | 2 | P0 |
| F7 | **Beyond the PDF**: sourced context, current versions/changes, real-world usage, alternatives | 3 | P0 |
| F8 | Ask AI chat: answers split "From your PDF" / "Beyond the PDF" with citations | 1–3 | P0 |
| F9 | Notes and highlights linked to page/text; code snippets inside notes | 5 | P0 |
| F10 | **Practice**: MCQs, explain-it-back (AI checks my answer), interview-style questions | 4 | P0 |
| F11 | Flashcards from notes, concepts, selections | 5 | P0 |
| F12 | **Mastery dashboard**: score per concept, per PDF, weak concepts, "fix now" | 6 | P0 |

### Release 2 — "Become an expert across tech"
| # | Feature |
|---|---|
| F13 | **Knowledge map**: all concepts from all PDFs grouped by domain (Backend, Security, AI…), coloured by mastery |
| F14 | **Cross-PDF links**: "this concept also appears in *DDIA* p. 210 and *RFC 9110* §9" |
| F15 | Spaced repetition: "Due today" across everything |
| F16 | **Hands-on tasks**: "build it" exercises with acceptance criteria (e.g., implement rate limiting in Spring Boot, exploit and fix an XSS in a sample Next.js page in a local lab) |
| F17 | **System design practice**: design prompts generated from what I've read; AI reviews my design |
| F18 | **Learning roadmap / gap finder**: compares my knowledge map against a target skill tree (e.g., "Senior backend engineer", "AppSec engineer", "AI engineer") and suggests what to read next, with sourced resources |
| F19 | Revision sheets per chapter/PDF (export Markdown) |

### Later
Import web pages/docs (not just PDFs) · YouTube transcript import · code-repo reading mode · audio summaries · Anki export.

### Not doing (personal tool)
Multi-user accounts, pricing/plans, quotas for others, featured library, exam features, marketing site.

---

## 6. Feature Requirements & Acceptance Criteria

### F2 — Upload & Library
- `.pdf`, ≤ 100 MB, ≤ 1,500 pages (big tech books).
- On upload: extract text per page, detect code blocks, auto-suggest domain tags (e.g., *Backend, Distributed Systems*); I can edit them.
- AC: [ ] Library shows title, domain tags, progress, mastery %. [ ] Scanned/image PDFs are read from the page image.

### F4 — Understand
- **Simple English:** short sentences; jargon explained in brackets; code in the page explained line by line when present.
- **मराठी/हिन्दी:** optional toggle; technical terms stay in English.
- AC: [ ] Meaning preserved, no outside facts. [ ] Streams, first words < 2 s. [ ] Cached once generated.

### F6 — Deepen ⭐
For each concept on a page:
| Part | Example (concept: *idempotency*) |
|---|---|
| Definition | Doing the same request twice has the same effect as doing it once |
| Why it matters | Safe retries after network failures |
| Code in my stack | Spring Boot endpoint with an `Idempotency-Key` header + DB unique constraint |
| Diagram | Mermaid sequence diagram: client → retry → server dedupe |
| Trade-offs | Storage for keys, expiry policy |
| Common mistakes | Treating POST as idempotent by default |
| See also | Other pages/PDFs that mention it |
- AC: [ ] Code is runnable and labelled with language/version. [ ] Diagrams render in the panel. [ ] Concepts saved for practice and mastery.

### F7 — Beyond the PDF ⭐
- Sections: **Background** · **What's current** (new versions, deprecations, CVEs, newer papers) · **In the real world** (how companies use it) · **Alternatives**.
- Every claim has a citation (title, site, URL, date accessed). Unsourced claims are hidden.
- Warns when the PDF is outdated (e.g., "this book uses Java 8; since Java 21 you can use virtual threads — [JEP 444]").
- AC: [ ] Visually separate from PDF content. [ ] Save to notes with the citation. [ ] Cached per concept; "Refresh" button.

### F8 — Ask AI
- Answer layout: 📄 From your PDF (page refs) → 🌐 Beyond (sources, only if needed) → 3 follow-ups.
- Can produce code, diagrams and comparison tables.

### F10 — Practice
| Type | Example |
|---|---|
| MCQ | "Which isolation level prevents non-repeatable reads?" |
| Explain-it-back | "Explain the CAP theorem in your own words." AI scores it against the PDF and shows gaps |
| Interview question | "How would you make a payment API safe to retry?" |
| Code reading | "What does this snippet print / where is the vulnerability?" |
- AC: [ ] Each question linked to a concept and page. [ ] Explanations after each answer. [ ] "Practise weak concepts".

### F12 — Mastery
- Mastery score per concept (0–100) from practice accuracy, explain-it-back quality, flashcards, and recency (decays over time).
- Stages: *Read → Understood → Practised → Mastered*.
- AC: [ ] Dashboard: mastery per PDF and per domain, 5 weakest concepts with "Fix now", streak.

---

## 7. Non-Functional Requirements

| Area | Requirement |
|---|---|
| Accuracy | PDF zone uses only the PDF; Beyond zone shows only sourced claims |
| Performance | Cached content < 1 s; AI first token < 2 s; Beyond results < 15 s with progress |
| Cost | Monthly budget cap on my Claude API key (e.g., $20) with a usage meter; aggressive caching |
| Privacy | Runs locally or on my own server; my PDFs never leave except as text sent to the Claude API |
| Security | If deployed: single login, HTTPS, API key server-side only. Also a chance to practise AppSec on my own app |
| Simplicity | Runs with one `docker compose up` |

---

## 8. Personal Success Metrics

| Metric | Target |
|---|---|
| Concepts mastered per week | ≥ 20 |
| PDFs completed to ≥ 80% mastery per month | ≥ 1 |
| Practice sessions per week | ≥ 4 |
| Domains with ≥ 50 mastered concepts after 6 months | ≥ 5 of 9 |
| Monthly API cost | ≤ budget cap |

---

## 9. Starter Reading List (first PDFs to master)

Prefer free/legal PDFs; buy the others.

| Domain | Start with |
|---|---|
| Distributed systems / backend | *Designing Data-Intensive Applications* (Kleppmann) |
| Software engineering | *A Philosophy of Software Design* (Ousterhout); *The Pragmatic Programmer* |
| Web / HTTP | RFC 9110 (HTTP Semantics), RFC 6749 (OAuth 2.0) — free |
| Security | OWASP Top 10 and OWASP ASVS — free; PortSwigger Web Security Academy (web) |
| AI / LLMs | "Attention Is All You Need" (arXiv) — free; *Hands-On Machine Learning* (Géron) |
| OS / fundamentals | *Operating Systems: Three Easy Pieces* — free |
| Reliability / DevOps | Google *Site Reliability Engineering* — free online |
| Frontend | MDN + React/Next.js docs (exported to PDF, or web import later) |

---

## 10. Risks & Mitigations

| Risk | Mitigation |
|---|---|
| Scope creep ("all of tech") | MVP = master **one PDF** end to end; knowledge map in R2 |
| Bad or outdated web sources | Trusted-domain list; date shown; outdated warnings |
| AI code examples wrong | Label language/version; "Run it yourself" note; explain-it-back catches misunderstanding |
| API cost | Caching, cheaper model for small tasks, budget cap |
| Building the tool instead of learning | Ship MVP in ~4–6 weeks; use it daily from week 3 |

---

## 11. Decisions
1. Marathi/Hindi toggle: **kept in MVP**.
2. Runs on **laptop only (localhost)**; deploy later if needed.
3. First real test PDF: ***Designing Data-Intensive Applications***.
4. Claude API budget: **$20/month**, enforced in the app.
