// Fake Gemini API for tests: generateContent + streamGenerateContent?alt=sse, JSON-schema answers,
// Google Search grounding (incl. one untrusted site), images. Usage: node fake-gemini.mjs [request-log.jsonl]
import http from 'node:http';
import fs from 'node:fs';
const OUT = process.argv[2] ?? '/dev/null';

const usage = { promptTokenCount: 1200, candidatesTokenCount: 300, totalTokenCount: 1500 };
const DEEPEN = '## What it is\nRetrying is safe when an operation is **idempotent**.\n\n## Why it matters\n- Networks time out\n\n## In my stack\n```java\n// Java 21, Spring Boot 3\n@PostMapping("/pay") void pay(@RequestHeader("Idempotency-Key") String key) {}\n```\n\n## Diagram\n```mermaid\nsequenceDiagram\n    participant C as Client\n    participant S as Server\n    C->>S: POST key K1\n    S-->>C: saved result\n```\n\n## Trade-offs\n- Key storage\n\n## Common mistakes\n- Assuming POST is idempotent\n';
const ASK = 'Retries are safe only for **idempotent** calls [p.1]. The page does not cover the newer header standard.\n<<<BEYOND>>>\nIdempotency-Key HTTP header\n<<<FOLLOWUPS>>>\n1. Is PUT idempotent?\n2. How long to keep keys?\n3. What about DELETE?';

http.createServer((req, res) => {
  let body = '';
  req.on('data', (c) => (body += c));
  req.on('end', () => {
    const b = JSON.parse(body);
    fs.appendFileSync(OUT, JSON.stringify({ url: req.url, key: req.headers['x-goog-api-key'], body: b }).slice(0, 4000) + '\n');
    if (req.headers['x-goog-api-key'] === 'rate-limited') {
      res.writeHead(429, { 'content-type': 'application/json' });
      return res.end(JSON.stringify({ error: { code: 429, message: 'Resource exhausted', status: 'RESOURCE_EXHAUSTED' } }));
    }
    const parts = b.contents[0].parts;
    const prompt = parts.map((p) => p.text ?? '').join('\n');
    const reply = (text, extra = {}) => ({ candidates: [{ content: { role: 'model', parts: [{ text }] }, finishReason: 'STOP', ...extra }], usageMetadata: usage });
    const json = (obj) => { res.writeHead(200, { 'content-type': 'application/json' }); res.end(JSON.stringify(obj)); };

    if (parts.some((p) => p.inline_data)) return json(reply('OCR TEXT read by Gemini from the page image.'));

    if (b.tools?.some((t) => t.google_search)) {
      const text = "## What's current\n* An IETF draft standardises the Idempotency-Key header for POST.\n* A random blog says retries are magic.\n\n## In the real world\n* Stripe accepts idempotency keys on POST requests.";
      return json(reply(text, { groundingMetadata: {
        webSearchQueries: ['idempotency key header'],
        groundingChunks: [
          { web: { uri: 'https://vertexaisearch.cloud.google.com/grounding-api-redirect/ietf', title: 'datatracker.ietf.org' } },
          { web: { uri: 'https://vertexaisearch.cloud.google.com/grounding-api-redirect/blog', title: 'random-blog.example' } },
          { web: { uri: 'https://vertexaisearch.cloud.google.com/grounding-api-redirect/stripe', title: 'docs.stripe.com' } },
        ],
        groundingSupports: [
          { segment: { startIndex: 21, endIndex: 84, text: 'An IETF draft standardises the Idempotency-Key header for POST.' }, groundingChunkIndices: [0] },
          { segment: { startIndex: 87, endIndex: 125, text: 'A random blog says retries are magic.' }, groundingChunkIndices: [1] },
          { segment: { startIndex: 150, endIndex: 199, text: 'Stripe accepts idempotency keys on POST requests.' }, groundingChunkIndices: [2] },
        ],
      } }));
    }

    if (b.generationConfig?.responseJsonSchema) {
      let obj;
      if (prompt.includes('List the technical concepts')) obj = { concepts: [
        { name: 'Idempotence', domain: 'DISTRIBUTED_SYSTEMS', definition: 'Doing an operation twice has the same effect as once.' },
        { name: 'Request ID', domain: 'BACKEND', definition: 'A unique ID that lets a server ignore duplicate requests.' }] };
      else if (prompt.includes('Write practice questions')) obj = { questions: [
        { type: 'MCQ', prompt: 'Which makes a payment API safe to retry?', options: ['Use POST everywhere', 'Idempotency key + UNIQUE constraint', 'Longer timeout', 'Retry until success'], answerIndex: 1, modelAnswer: 'Idempotency key.', explanation: 'Page 1.' },
        { type: 'EXPLAIN', prompt: 'Explain idempotence in your own words.', options: [], answerIndex: -1, modelAnswer: 'Same effect when repeated.', explanation: 'Page 1.' }] };
      else if (prompt.includes('Grade the answer')) obj = { score: 80, missing: [], feedback: 'Good explanation.' };
      return json(reply(JSON.stringify(obj)));
    }

    const text = prompt.includes('My question:') ? ASK
      : prompt.includes('Teach me this concept') ? DEEPEN
      : prompt.includes('<<<TERMS>>>') ? 'Simple page explanation.\n<<<NATIVE>>>\nसोपे स्पष्टीकरण.\n<<<TERMS>>>\nretry :: send again'
      : 'Selection explanation.';
    if (!req.url.includes('streamGenerateContent')) return json(reply(text));

    res.writeHead(200, { 'content-type': 'text/event-stream' });
    const chunks = text.match(/[\s\S]{1,16}/g);
    let i = 0;
    res.write(`data: ${JSON.stringify({ candidates: [{ content: { role: 'model', parts: [{ text: 'thinking...', thought: true }] } }] })}\r\n\r\n`);
    const t = setInterval(() => {
      if (i < chunks.length) {
        const last = i === chunks.length - 1;
        const chunk = { candidates: [{ content: { role: 'model', parts: [{ text: chunks[i++] }] }, ...(last ? { finishReason: 'STOP' } : {}) }], ...(last ? { usageMetadata: usage } : {}) };
        return res.write(`data: ${JSON.stringify(chunk)}\r\n\r\n`);
      }
      clearInterval(t);
      res.end();
    }, 15);
  });
}).listen(9098, '127.0.0.1');
