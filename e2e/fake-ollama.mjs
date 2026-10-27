// Fake Ollama /api/chat for tests: NDJSON streaming, `format` (JSON schema) answers, images, 404 for unknown models.
// Usage: node fake-ollama.mjs [request-log.jsonl]
import http from 'node:http';
import fs from 'node:fs';
const OUT = process.argv[2] ?? '/dev/null';
const INSTALLED = ['qwen3.5:4b', 'qwen3.5:2b'];

const DEEPEN = '## What it is\nRetrying is safe when an operation is **idempotent**.\n\n## In my stack\n```java\n// Java 21\nvoid pay(String key) {}\n```\n\n## Diagram\n```mermaid\nsequenceDiagram\n    participant C as Client\n    participant S as Server\n    C->>S: POST key K1\n    S-->>C: saved result\n```\n';
const ASK = 'Retries are safe only for **idempotent** calls [p.1].\n<<<BEYOND>>>\nIdempotency-Key header\n<<<FOLLOWUPS>>>\n1. Is PUT idempotent?\n2. How long to keep keys?\n3. What about DELETE?';

http.createServer((req, res) => {
  let body = '';
  req.on('data', (c) => (body += c));
  req.on('end', () => {
    const b = JSON.parse(body);
    fs.appendFileSync(OUT, JSON.stringify({ ...b, messages: b.messages.map((m) => ({ ...m, images: m.images ? ['…'] : undefined })) }).slice(0, 3000) + '\n');
    if (!INSTALLED.includes(b.model)) {
      res.writeHead(404, { 'content-type': 'application/json' });
      return res.end(JSON.stringify({ error: `model '${b.model}' not found` }));
    }
    const user = b.messages[b.messages.length - 1];
    const prompt = user.content;
    const done = { done: true, done_reason: 'stop', prompt_eval_count: 900, eval_count: 150 };
    const json = (content) => { res.writeHead(200, { 'content-type': 'application/json' }); res.end(JSON.stringify({ model: b.model, message: { role: 'assistant', content }, ...done })); };

    if (user.images) return json('OCR TEXT read by the local model from the page image.');
    if (b.format) {
      let obj;
      if (prompt.includes('List the technical concepts')) obj = { concepts: [
        { name: 'Idempotence', domain: 'DISTRIBUTED_SYSTEMS', definition: 'Doing an operation twice has the same effect as once.' },
        { name: 'Request ID', domain: 'BACKEND', definition: 'A unique ID that lets a server ignore duplicates.' }] };
      else if (prompt.includes('Write practice questions')) obj = { questions: [
        { type: 'MCQ', prompt: 'Which makes a payment API safe to retry?', options: ['Use POST everywhere', 'Idempotency key + UNIQUE constraint', 'Longer timeout', 'Retry until success'], answerIndex: 1, modelAnswer: 'Idempotency key.', explanation: 'Page 1.' },
        { type: 'EXPLAIN', prompt: 'Explain idempotence in your own words.', options: [], answerIndex: -1, modelAnswer: 'Same effect when repeated.', explanation: 'Page 1.' }] };
      else if (prompt.includes('Grade the answer')) obj = { score: 70, missing: [], feedback: 'Fine.' };
      return json(JSON.stringify(obj));
    }
    const text = prompt.includes('My question:') ? ASK
      : prompt.includes('Teach me this concept') ? DEEPEN
      : prompt.includes('<<<TERMS>>>') ? 'Simple page explanation.\n<<<NATIVE>>>\nसोपे स्पष्टीकरण.\n<<<TERMS>>>\nretry :: send again'
      : 'Selection explanation.';
    if (!b.stream) return json(text);

    res.writeHead(200, { 'content-type': 'application/x-ndjson' });
    const chunks = text.match(/[\s\S]{1,12}/g);
    let i = 0;
    const t = setInterval(() => {
      if (i < chunks.length) return res.write(JSON.stringify({ model: b.model, message: { role: 'assistant', content: chunks[i++] }, done: false }) + '\n');
      clearInterval(t);
      res.write(JSON.stringify({ model: b.model, message: { role: 'assistant', content: '' }, ...done }) + '\n');
      res.end();
    }, 10);
  });
}).listen(11434, '127.0.0.1');
