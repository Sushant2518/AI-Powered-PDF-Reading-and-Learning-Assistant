// Fake Claude API for tests: speaks the real Messages wire format (streaming, structured output,
// web search with citations + pause_turn, images). Usage: node fake-claude.mjs [request-log.jsonl]
import http from 'node:http';
import fs from 'node:fs';
const OUT = process.argv[2] ?? '/dev/null';

const DEEPEN = '## What it is\nRetrying is safe when an operation is **idempotent**.\n\n## Why it matters\n- Networks time out\n- Clients retry\n\n## In my stack\n```java\n// Java 21, Spring Boot 3\n@PostMapping("/pay") void pay(@RequestHeader("Idempotency-Key") String key) {}\n```\n\n## Diagram\n```mermaid\nsequenceDiagram\n    participant C as Client\n    participant S as Server\n    C->>S: POST key K1\n    S-->>C: timeout\n    C->>S: POST key K1 retry\n    S-->>C: saved result\n```\n\n## Trade-offs\n- Key storage\n\n## Common mistakes\n- Assuming POST is idempotent\n';
const ASK = 'Retries are safe only for **idempotent** calls [p.3]. The page does not cover the newer header standard [p.4].\n<<<BEYOND>>>\nIdempotency-Key HTTP header\n<<<FOLLOWUPS>>>\n1. Is PUT idempotent?\n2. How long to keep keys?\n3. What about DELETE?';

http.createServer((req, res) => {
  let body = '';
  req.on('data', c => body += c);
  req.on('end', () => {
    const b = JSON.parse(body);
    fs.appendFileSync(OUT, JSON.stringify({ beta: req.headers['anthropic-beta'], body: b }) + '\n');
    const prompt = JSON.stringify(b.messages);
    const usage = { input_tokens: 1500, output_tokens: 400, cache_creation_input_tokens: 0, cache_read_input_tokens: 0 };
    const json = (obj) => { res.writeHead(200, { 'content-type': 'application/json' }); res.end(JSON.stringify(obj)); };

    if (b.tools?.some(t => t.type?.startsWith('web_search'))) {
      // First call pauses (pause_turn) to exercise the resume loop; the resumed call answers with citations.
      const resumed = b.messages.length > 1;
      const cite = (url, title, text) => ({ type: 'text', text, citations: [{ type: 'web_search_result_location', url, title, encrypted_index: 'eA', cited_text: text }] });
      const content = resumed ? [
        { type: 'text', text: "I'll look that up. Here's what I found:\n\n## What's current\n- ", citations: null },
        cite('https://datatracker.ietf.org/doc/draft-ietf-httpapi-idempotency-key-header/', 'The Idempotency-Key HTTP Header Field', 'An IETF draft standardises the Idempotency-Key header for POST.'),
        { type: 'text', text: '\n- Most people think this is easy.\n\n## In the real world\n- ', citations: null },
        cite('https://docs.stripe.com/api/idempotent_requests', 'Idempotent requests | Stripe', 'Stripe accepts idempotency keys on POST requests.'),
      ] : [
        { type: 'server_tool_use', id: 'srvtoolu_1', name: 'web_search', input: { query: 'idempotency key header' } },
        { type: 'web_search_tool_result', tool_use_id: 'srvtoolu_1', content: [{ type: 'web_search_result', url: 'https://docs.stripe.com/api/idempotent_requests', title: 'Idempotent requests', encrypted_content: 'ZZ', page_age: null }] },
      ];
      return json({ id: 'msg_w', type: 'message', role: 'assistant', model: 'claude-opus-5-5', stop_reason: resumed ? 'end_turn' : 'pause_turn',
        stop_sequence: null, content, usage: { ...usage, input_tokens: 9000, server_tool_use: { web_search_requests: resumed ? 1 : 2 } } });
    }

    if (Array.isArray(b.messages[0].content) && b.messages[0].content.some(c => c.type === 'image')) {
      return json({ id: 'msg_o', type: 'message', role: 'assistant', model: b.model, stop_reason: 'end_turn', stop_sequence: null,
        content: [{ type: 'text', text: 'OCR TEXT read by Claude from the page image.' }], usage });
    }

    if (!b.stream && prompt.includes('Write practice questions')) {
      const mcq = (p, opts, i) => ({ type: 'MCQ', prompt: p, options: opts, answerIndex: i, modelAnswer: 'Because repeating it has no extra effect.', explanation: 'Page 3 says retries are safe only when idempotent.' });
      const questions = { questions: [
        mcq('Which makes a payment API safe to retry?', ['Use POST everywhere', 'Idempotency key + UNIQUE constraint', 'Longer timeout', 'Retry until success'], 1),
        mcq('Which HTTP method is idempotent?', ['POST', 'PATCH', 'PUT', 'CONNECT'], 2),
        mcq('Broken question with 2 options', ['a', 'b'], 0),
        { type: 'EXPLAIN', prompt: 'Explain idempotence in your own words and when it matters.', options: [], answerIndex: -1, modelAnswer: 'Same effect when repeated; matters for safe retries after timeouts.', explanation: 'p.3' },
        { type: 'INTERVIEW', prompt: 'How would you make a payment endpoint safe to retry?', options: [], answerIndex: -1, modelAnswer: 'Idempotency key, store result, unique constraint, TTL.', explanation: 'p.3' } ] };
      return json({ id: 'msg_p', type: 'message', role: 'assistant', model: b.model, stop_reason: 'end_turn', stop_sequence: null,
        content: [{ type: 'text', text: JSON.stringify(questions) }], usage });
    }

    if (!b.stream && prompt.includes('Grade the answer')) {
      const good = /retr/i.test(prompt.split('<answer>')[1] ?? '');
      const grade = good ? { score: 82, missing: ['Mention storing the response'], feedback: 'Good grasp of retries.' }
                         : { score: 30, missing: ['Same effect when repeated', 'Safe retries'], feedback: 'Too vague.' };
      return json({ id: 'msg_g', type: 'message', role: 'assistant', model: b.model, stop_reason: 'end_turn', stop_sequence: null,
        content: [{ type: 'text', text: JSON.stringify(grade) }], usage });
    }

    if (!b.stream) {
      const concepts = { concepts: [
        { name: 'Idempotence', domain: 'DISTRIBUTED_SYSTEMS', definition: 'Doing an operation twice has the same effect as once.' },
        { name: 'Prompt Caching', domain: 'AI_ML', definition: 'Reusing an already-processed prompt prefix to save cost.' } ] };
      return json({ id: 'msg_s', type: 'message', role: 'assistant', model: 'claude-opus-5-5', stop_reason: 'end_turn', stop_sequence: null,
        content: [{ type: 'text', text: JSON.stringify(concepts) }], usage });
    }

    res.writeHead(200, { 'content-type': 'text/event-stream' });
    const ev = (type, data) => res.write(`event: ${type}\ndata: ${JSON.stringify({ type, ...data })}\n\n`);
    const text = prompt.includes('My question:') ? ASK
      : prompt.includes('Teach me this concept') ? DEEPEN
      : prompt.includes('<<<TERMS>>>') ? 'Simple page explanation.\n<<<NATIVE>>>\nसोपे स्पष्टीकरण.\n<<<TERMS>>>\nretry :: send again'
      : 'Selection explanation.';
    ev('message_start', { message: { id: 'msg_1', type: 'message', role: 'assistant', model: 'claude-opus-5-5', content: [], stop_reason: null, stop_sequence: null, usage: { ...usage, output_tokens: 1 } } });
    ev('content_block_start', { index: 0, content_block: { type: 'text', text: '' } });
    const chunks = text.match(/[\s\S]{1,16}/g); let i = 0;
    const t = setInterval(() => {
      if (i < chunks.length) return ev('content_block_delta', { index: 0, delta: { type: 'text_delta', text: chunks[i++] } });
      clearInterval(t);
      ev('content_block_stop', { index: 0 });
      ev('message_delta', { delta: { stop_reason: 'end_turn', stop_sequence: null }, usage: { output_tokens: 400 } });
      ev('message_stop', {}); res.end();
    }, 15);
  });
}).listen(9099, '127.0.0.1');
