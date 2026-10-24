# UI Design — Personal Tech Mastery Companion

> Version 3.0 · Companion to [PRD.md](./PRD.md) and [TRD.md](./TRD.md)
> **Clickable mockup:** [`ui-mockup.html`](./ui-mockup.html) → `open docs/ui-mockup.html`

---

## 1. Design Principles

1. **The PDF is the hero.** Learning tools sit beside it, never on top of it.
2. **One click from text to help.** Select → Explain / Deepen / Beyond / Ask / Note.
3. **Two zones look different.** 📄 *From your PDF* = blue accent + page chips. 🌐 *Beyond the PDF* = green accent + source cards. They are never mixed.
4. **Show, don't just tell.** Code and diagrams are first-class in the panel.
5. **Progress is visible.** Every PDF, chapter and concept shows its mastery.

---

## 2. Design Tokens

| Token | Light | Dark | Use |
|---|---|---|---|
| `bg` | `#F7F6F2` | `#16171A` | App background |
| `surface` | `#FFFFFF` | `#1F2125` | Panels, cards |
| `text` / `muted` | `#1E1F22` / `#6B6F76` | `#ECECEC` / `#9A9EA6` | Text |
| `border` | `#E4E2DC` | `#2E3036` | Dividers |
| `pdf` (primary) | `#3B5BDB` | `#7C93F5` | From-your-PDF zone, buttons, active tab |
| `beyond` | `#0C8A6E` | `#3CC9A6` | Beyond-the-PDF zone, sources |
| `code-bg` | `#F1F3F5` | `#111214` | Code blocks |
| mastery | red `<40` · amber `40–79` · green `≥80` | same | Mastery bars and dots |

**Type:** Inter (UI, 14 px; explanations 16 px / 1.7) · JetBrains Mono (code, 13 px) · Noto Sans Devanagari (Marathi/Hindi, 17 px / 1.8).
**Spacing:** 4-px grid. Radius 8 px. Shadow only on floating elements.

---

## 3. Sitemap

```
Library ──► Reader (core)
   │          ├─ Understand   (Simple EN · मराठी/हिन्दी · Original)
   │          ├─ Deepen       (concepts → definition, why, code, diagram, trade-offs)
   │          ├─ Beyond       (sourced: background · current · real world · alternatives)
   │          ├─ Ask          (chat: 📄 PDF answer + 🌐 Beyond answer)
   │          └─ Notes
   ├──► Practice   (MCQ · explain-back · interview · code reading)
   ├──► Mastery    (per PDF, per domain, weakest concepts; R2: knowledge map)
   └──► Settings   (language, style, budget meter)
```

---

## 4. Screens

### 4.1 Library
```
┌───────────────────────────────────────────────────────────────────────────┐
│ 📖 SamajhPDF   Library  Practice  Mastery             $7.40 / $20   ⚙    │
├───────────────────────────────────────────────────────────────────────────┤
│ Continue: DDIA · Ch.11 Stream Processing · p. 472   mastery ████░░ 41%  ▶ │
│                                                                           │
│ Filter: [All] [Backend] [Distributed] [Security] [AI] [Frontend] …  [+ Upload] │
│ ┌────────────┐ ┌────────────┐ ┌────────────┐ ┌ ─ ─ ─ ─ ─ ┐               │
│ │ ▒▒▒▒▒▒▒▒▒▒ │ │ ▒▒▒▒▒▒▒▒▒▒ │ │ ▒▒▒▒▒▒▒▒▒▒ │   Drop PDF                    │
│ │ DDIA       │ │ OWASP Top10│ │ Attention  │ │  here      │               │
│ │ Distributed│ │ Security   │ │ AI · paper │                             │
│ │ 562 pg     │ │ 38 pg      │ │ 15 pg      │ └ ─ ─ ─ ─ ─ ┘               │
│ │ read 34%   │ │ read 100%  │ │ processing │                             │
│ │ ● 41% mast.│ │ ● 82% mast.│ │ ◌          │                             │
│ └────────────┘ └────────────┘ └────────────┘                             │
└───────────────────────────────────────────────────────────────────────────┘
```
- Cards show domain tags, read %, mastery % (coloured dot).
- States: empty (drop zone + "Try OWASP Top 10, free"), processing, "read via OCR" badge, error/retry.

### 4.2 Reader — core screen
```
┌──────────────────────────────────────────────────────────────────────────────────┐
│ ← │ DDIA · Ch.11 │ ◀ 472/562 ▶ │ − 100% + │ 🌐 मराठी ▾ │ ☾ │ mastery ● 41%       │
├───────────────────────────────────────────┬──────────────────────────────────────┤
│                                           │ Understand Deepen Beyond Ask Notes   │
│  ┌─────────────────────────────────────┐  │──────────────────────────────────────│
│  │ Idempotence                         │  │ 📄 FROM YOUR PDF · p.472             │
│  │                                     │  │ [Simple English] [मराठी] [Original]  │
│  │ An operation is idempotent if       │  │                                      │
│  │ performing it multiple times has    │  │ If you do the same thing twice,      │
│  │ the same effect as performing it    │  │ the result is the same as doing it   │
│  │ once. ▓▓Retries after a network    │  │ once. That makes retrying safe...    │
│  │ failure▓▓ are only safe when ...    │  │                                      │
│  │                                     │  │ Key terms: (idempotent) (retry)      │
│  │          (pdf.js page)              │  │ (exactly-once)                       │
│  └─────────────────────────────────────┘  │                                      │
│                                           │ [Deepen this page →] [🌐 Go beyond]  │
│                                           │ 👍 👎 ↻  [+ Note]                     │
└───────────────────────────────────────────┴──────────────────────────────────────┘
               ~58% (resizable ⇔)                        ~42%
```
- The panel follows the visible page. Last-used tab is remembered.
- Streaming: skeleton → word-by-word text. Page chips (`p.472`) are clickable.

### 4.3 Selection menu
```
  ┌─────────────────────────────────────────────────────────────────────────────┐
  │ 💬 Explain │ 🌐 Translate │ 🔬 Deepen │ 🧭 Beyond │ ❓ Ask │ 📝 Note │ 🃏 Card │ ●●●● │
  └─────────────────────────────────────────────────────────────────────────────┘
```
Results open as a **Selection card** at the top of the relevant tab (quote → answer → Save to notes · Ask follow-up · Close).

### 4.4 Deepen tab
```
┌──────────────────────────────────────┐
│ 📄 Concepts on p.472                 │
│ (Idempotence ●62) (Exactly-once ●20) │  ← chips with mastery dots
│──────────────────────────────────────│
│ IDEMPOTENCE                 Distributed│
│ Definition  Same request twice = same │
│             effect as once.           │
│ Why it matters  Safe retries after    │
│             timeouts.                 │
│ In your stack  [Java/Spring ▾]        │
│ ┌──────────────────────────────────┐ │
│ │ @PostMapping("/payments")        │ │
│ │ ResponseEntity<?> pay(           │ │
│ │  @RequestHeader("Idempotency-Key")│ │
│ │  ...                     [Copy]  │ │
│ └──────────────────────────────────┘ │
│ Diagram (Mermaid sequence)            │
│ Trade-offs • key storage • expiry     │
│ Common mistakes • assuming POST is…   │
│ Also in: RFC 9110 §9.2.2 (your PDF)   │
│ [🃏 Make flashcards] [🎯 Practise]    │
└──────────────────────────────────────┘
```

### 4.5 Beyond tab (visually distinct, green)
```
┌──────────────────────────────────────┐
│ 🌐 BEYOND THE PDF · Idempotence      │
│ Not from your book. Every point has  │
│ a source.                      ↻     │
│──────────────────────────────────────│
│ ⚠ Your PDF is from 2017. Since then: │
│ • An IETF draft standardises the     │
│   Idempotency-Key header  [1]        │
│ Background                           │
│ • HTTP defines PUT/DELETE as         │
│   idempotent methods  [2]            │
│ In the real world                    │
│ • Payment APIs accept idempotency    │
│   keys on POST  [3]                  │
│──────────────────────────────────────│
│ Sources                              │
│ [1] datatracker.ietf.org · accessed  │
│ [2] rfc-editor.org · RFC 9110        │
│ [3] docs.stripe.com                  │
│ [+ Save to notes with sources]       │
└──────────────────────────────────────┘
```

### 4.6 Ask tab
- Answer card has two labelled parts: **📄 From your PDF** (page chips) and **🌐 Beyond the PDF** (only if needed or asked; numbered sources).
- Composer: quote chip · "Allow Beyond" toggle · Answer in: EN / मराठी / Both.
- 3 follow-up chips after every answer.

### 4.7 Notes tab
Notes grouped by chapter. Each note shows a quote (highlight colour bar), my markdown (code allowed), and its source badge: `📄 p.472` or `🌐 3 sources`. Actions: edit · jump to text · make flashcard · delete.

### 4.8 Practice
```
┌─────────────────────────────────────────────┐   ┌─────────────────────────────────────────────┐
│ Practice · DDIA Ch.11 · 3/10   [MCQ]        │   │ Practice · 4/10       [Explain it back]     │
│ Which makes a payment API safe to retry?    │   │ Explain idempotence in your own words and   │
│ ○ Use POST everywhere                       │   │ when it matters.                            │
│ ● Idempotency key + unique constraint       │   │ ┌─────────────────────────────────────────┐ │
│ ○ Increase timeout                          │   │ │ (I type my answer…)                     │ │
│ ✅ Correct — p.472 explains…                │   │ └─────────────────────────────────────────┘ │
│ concept: Idempotence  ● 62 → 68             │   │ [Submit]  → score 72/100                    │
│                              [Next →]       │   │ Missing: mention of duplicate side effects  │
└─────────────────────────────────────────────┘   └─────────────────────────────────────────────┘
```
Modes: by chapter · by concepts · **weak concepts** · random review.

### 4.9 Mastery dashboard
```
┌──────────────────────────────────────────────────────────────────────┐
│ Mastery                       This week: +23 concepts mastered 🔥 5d │
│                                                                      │
│ By domain                         Weakest concepts                   │
│ Distributed  ██████░░░░ 58%       ● Exactly-once         20  [Fix]   │
│ Security     ████████░░ 82%       ● Linearizability      31  [Fix]   │
│ AI / LLMs    ██░░░░░░░░ 18%       ● Two-phase commit     38  [Fix]   │
│ Backend      █████░░░░░ 47%       ● CSRF tokens          44  [Fix]   │
│ Frontend     ░░░░░░░░░░  0%       ● Attention heads      45  [Fix]   │
│                                                                      │
│ By PDF:  DDIA ● 41%  ·  OWASP Top 10 ● 82%  ·  Attention ● 18%       │
│ (R2) [View knowledge map →]                                          │
└──────────────────────────────────────────────────────────────────────┘
```
"Fix" opens the concept's page in the reader with the Deepen tab, then a 3-question practice set.

### 4.10 Settings
Language (none / मराठी / हिन्दी) · Explanation style · Theme · **Budget** (meter, monthly cap, "allow over budget this month") · Trusted sources list (add/remove domains).

---

## 5. Key Flows

**A — Study a page:** open DDIA → page 472 → Understand streams in → select "exactly-once" → 🔬 Deepen → read code + diagram → 🧭 Beyond → save note with sources.
**B — Lock it in:** Deepen → "🎯 Practise" → 5 questions → explain-back graded → mastery 20 → 64.
**C — Weekly review:** Mastery → weakest concepts → Fix → reader jumps to the page → practice → green.

---

## 6. Responsive

| Width | Layout |
|---|---|
| ≥ 1200 px | PDF 58% + panel 42%, resizable |
| 768–1199 px | Panel collapsible, tabs become icons |
| < 768 px | PDF full width; panel = bottom sheet; selection menu = bottom action bar |

## 7. States

| Where | State | UI |
|---|---|---|
| Any AI tab | loading | Skeleton + "Reading p.472…" |
| Any AI tab | over budget | "Monthly budget reached ($20). [Allow this month] [Settings]" |
| Beyond | no trusted sources found | "No trusted source found. Nothing shown, so nothing unverified." |
| Ask | not in PDF | "Not in this PDF. [Search beyond →]" |
| Deepen | page has no concepts | "Nothing technical here (e.g., preface)." |
| Mastery | empty | "Practise a chapter to see mastery here." |
