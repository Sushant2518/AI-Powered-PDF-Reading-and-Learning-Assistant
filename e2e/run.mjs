// The whole mastery loop in a real browser (Chrome DevTools Protocol, no extra dependencies):
// upload → Understand → selection Explain → Deepen → note → Ask (+ Beyond) → Practice → Mastery.
// Usage: node run.mjs <pdf> <out-dir> <gemini|claude|local>
import fs from 'node:fs';

const [, , pdfPath, outDir, provider = 'gemini'] = process.argv;
const API = 'http://localhost:8080/api';
const APP = 'http://localhost:3000';
const sleep = (ms) => new Promise((r) => setTimeout(r, ms));

// ---------- tiny CDP client ----------
const target = await (await fetch('http://127.0.0.1:9333/json/new?about:blank', { method: 'PUT' })).json();
const ws = new WebSocket(target.webSocketDebuggerUrl);
let seq = 0;
const pending = new Map();
const errors = [];
ws.onmessage = (m) => {
  const d = JSON.parse(m.data);
  if (d.method === 'Runtime.exceptionThrown') errors.push(d.params.exceptionDetails.exception?.description ?? 'exception');
  pending.get(d.id)?.(d.result);
};
await new Promise((r) => (ws.onopen = r));
const send = (method, params = {}) => new Promise((r) => { pending.set(++seq, r); ws.send(JSON.stringify({ id: seq, method, params })); });
const ev = async (expr) => (await send('Runtime.evaluate', { expression: expr, awaitPromise: true, returnByValue: true }))?.result?.value;
const shot = async (name) => fs.writeFileSync(`${outDir}/${name}.png`, Buffer.from((await send('Page.captureScreenshot')).data, 'base64'));
const open = async (path) => { await send('Page.navigate', { url: APP + path }); await sleep(2500); };
const click = (scope, text) => ev(`(() => { const b = [...document.querySelectorAll(${JSON.stringify(scope + ' button')})].find(b => b.textContent.includes(${JSON.stringify(text)})); b?.click(); return !!b; })()`);
const waitFor = async (expr, what, ms = 15000) => {
  for (const end = Date.now() + ms; Date.now() < end; await sleep(250)) if (await ev(expr)) return;
  throw new Error(`Timed out waiting for: ${what}`);
};
const selectText = (minLength = 30) => ev(`(() => {
  const s = [...document.querySelectorAll('.textLayer span')].find(s => s.textContent.trim().length > ${minLength});
  const r = document.createRange(); r.selectNodeContents(s); getSelection().removeAllRanges(); getSelection().addRange(r);
  s.closest('.overflow-auto').dispatchEvent(new MouseEvent('mouseup', { bubbles: true })); return s.textContent; })()`);

let passed = 0;
async function step(name, fn) {
  try {
    await fn();
    passed++;
    console.log(`  ✓ ${name}`);
  } catch (e) {
    console.log(`  ✗ ${name}: ${e.message}`);
    await shot(`failed-${name.replace(/\W+/g, '-')}`).catch(() => {});
    process.exit(1);
  }
}

await send('Runtime.enable');
await send('Page.enable');
await send('Emulation.setDeviceMetricsOverride', { width: 1400, height: 900, deviceScaleFactor: 1, mobile: false });

// ---------- the flow ----------
let docId;
await step(`use ${provider} as the AI provider`, async () => {
  const res = await fetch(`${API}/settings`, { method: 'PUT', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ provider, monthlyBudgetUsd: 5 }) });
  if ((await res.json()).provider !== provider) throw new Error('provider not saved');
});

await step('upload and process a PDF', async () => {
  const body = new FormData();
  body.append('file', new Blob([fs.readFileSync(pdfPath)], { type: 'application/pdf' }), 'sample.pdf');
  docId = (await (await fetch(`${API}/documents`, { method: 'POST', body })).json()).id;
  for (let i = 0; i < 40; i++) {
    const d = await (await fetch(`${API}/documents/${docId}`)).json();
    if (d.status === 'READY') { if (d.pageCount !== 3) throw new Error(`expected 3 pages, got ${d.pageCount}`); return; }
    await sleep(500);
  }
  throw new Error('document never became READY');
});

await step('library shows the PDF', async () => {
  await open('/');
  await waitFor(`document.querySelector('main')?.innerText.includes('sample')`, 'library card');
});

await step('Understand streams a page explanation', async () => {
  await open(`/read/${docId}`);
  await waitFor(`!!document.querySelector('.textLayer span')`, 'PDF text layer');
  await waitFor(`document.querySelector('aside')?.innerText.includes('Simple page explanation')`, 'explanation');
});

await step('selecting text → Explain', async () => {
  await selectText();
  await sleep(300);
  if (!(await click('[role=menu]', 'Explain'))) throw new Error('selection menu missing');
  await waitFor(`document.querySelector('aside')?.innerText.includes('Selection explanation')`, 'selection answer');
});

await step('Deepen lists concepts and renders the diagram', async () => {
  await click('[role=tablist]', 'Deepen');
  await waitFor(`[...document.querySelectorAll('aside button')].some(b => b.textContent === 'Idempotence')`, 'concept chip');
  await click('aside', 'Idempotence');
  await waitFor(`!!document.querySelector('aside svg')`, 'mermaid diagram');
});

await step('note from a selection', async () => {
  await selectText();
  await sleep(300);
  await click('[role=menu]', 'Note');
  await waitFor(`document.activeElement?.tagName === 'TEXTAREA'`, 'note editor');
  await ev(`(() => { const t = document.activeElement; Object.getOwnPropertyDescriptor(HTMLTextAreaElement.prototype, 'value').set.call(t, 'my e2e note'); t.dispatchEvent(new Event('input', { bubbles: true })); })()`);
  for (let i = 0; i < 20; i++) {
    const notes = await (await fetch(`${API}/documents/${docId}/notes`)).json();
    if (notes.some((n) => n.content === 'my e2e note' && n.anchor?.rects?.length)) return;
    await sleep(250);
  }
  throw new Error('note was not autosaved with its anchor');
});

await step('Ask answers from the PDF and goes Beyond with sources', async () => {
  await click('[role=tablist]', 'Ask');
  await sleep(500);
  await ev(`(() => { const i = document.querySelector('aside form input'); Object.getOwnPropertyDescriptor(HTMLInputElement.prototype, 'value').set.call(i, 'Why are retries safe?'); i.dispatchEvent(new Event('input', { bubbles: true })); })()`);
  await sleep(100);
  await ev(`document.querySelector('aside form').requestSubmit()`);
  if (provider === 'local') {
    // No web search locally: the PDF answer stands alone, and no Beyond request is made.
    await waitFor(`document.querySelector('aside')?.innerText.includes('Retries are safe')`, 'PDF answer');
    await waitFor(`[...document.querySelectorAll('aside button')].some(b => b.textContent.startsWith('›'))`, 'follow-up chips (answer finished)');
    if (await ev(`document.querySelector('aside')?.innerText.includes('BEYOND THE PDF')`)) throw new Error('Beyond shown for local');
  } else {
    await waitFor(`document.querySelector('aside')?.innerText.includes('BEYOND THE PDF')`, 'beyond block');
    await waitFor(`document.querySelectorAll('aside a[target=_blank]').length > 0`, 'source link');
  }
  await shot(`ask-${provider}`);
});

await step('Practice: answer a question and see mastery change', async () => {
  const concepts = await (await fetch(`${API}/documents/${docId}/pages/1/concepts`)).json();
  await open(`/practice?documentId=${docId}&conceptIds=${concepts.map((c) => c.id).join(',')}`);
  await waitFor(`!!document.querySelector('main .rounded-xl p')`, 'first question');
  const isMcq = await ev(`document.querySelector('main')?.innerText.includes('Multiple choice')`);
  if (isMcq) await ev(`document.querySelectorAll('main .space-y-2 > button')[1].click()`);
  else {
    await ev(`(() => { const t = document.querySelector('main textarea'); Object.getOwnPropertyDescriptor(HTMLTextAreaElement.prototype, 'value').set.call(t, 'Repeating has the same effect so retries are safe'); t.dispatchEvent(new Event('input', { bubbles: true })); })()`);
    await sleep(100);
    await click('main', 'Submit for grading');
  }
  await waitFor(`document.querySelector('main')?.innerText.includes('mastery')`, 'mastery before → after');
  await shot(`practice-${provider}`);
});

await step('Mastery dashboard shows the practised concept', async () => {
  await open('/mastery');
  await waitFor(`document.querySelector('main')?.innerText.includes('Idempotence')`, 'concept in dashboard');
  await shot(`mastery-${provider}`);
});

await step('Settings page shows both providers', async () => {
  await open('/settings');
  await waitFor(`['Gemini (free)', 'Claude (paid)', 'Local (free, offline)'].every(t => document.querySelector('main')?.innerText.includes(t))`, 'provider options');
  // The Claude budget only matters (and only shows) when Claude is the provider.
  const budgetShown = await ev(`document.querySelector('main')?.innerText.includes('Monthly Claude budget')`);
  if (budgetShown !== (provider === 'claude')) throw new Error(`budget field shown=${budgetShown} for ${provider}`);
});

if (errors.length) {
  console.log(`  ✗ ${errors.length} uncaught browser error(s):\n    ${errors.slice(0, 3).join('\n    ')}`);
  process.exit(1);
}
console.log(`  ${passed} steps passed (${provider})`);
process.exit(0);
