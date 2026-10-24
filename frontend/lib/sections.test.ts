// Run: node --test --experimental-strip-types lib/sections.test.ts
import { test } from "node:test";
import assert from "node:assert/strict";
import { linkMarkers, parseAnswer, parseSections } from "./sections.ts";

test("splits an Ask answer", () => {
  const r = parseAnswer("Safe for idempotent calls [p.3].\n---\nमराठी उत्तर.\n<<<BEYOND>>>\nIdempotency-Key header\n<<<FOLLOWUPS>>>\n1. Is PUT idempotent?\n- Keys TTL?\n");
  assert.equal(r.text, "Safe for idempotent calls [p.3].");
  assert.equal(r.native, "मराठी उत्तर.");
  assert.equal(r.beyondQuery, "Idempotency-Key header");
  assert.deepEqual(r.followUps, ["Is PUT idempotent?", "Keys TTL?"]);
  assert.equal(parseAnswer("Streaming so far <<<BEY").text, "Streaming so far");
});

test("links page and source markers", () => {
  assert.equal(linkMarkers("Safe [p.3]. Draft [1] [2].", [{ n: 1, url: "https://ietf.org/x" }]),
    "Safe [p.3](#page-3). Draft [[1]](https://ietf.org/x) [2].");
  assert.equal(linkMarkers("Already [1](https://a.b) linked"), "Already [1](https://a.b) linked");
});

test("splits all three sections", () => {
  const r = parseSections("Simple text.\n<<<NATIVE>>>\nमराठी मजकूर.\n<<<TERMS>>>\n- **idempotent** :: same result if repeated\nretry :: send again :: after a timeout\n");
  assert.equal(r.simple, "Simple text.");
  assert.equal(r.native, "मराठी मजकूर.");
  assert.deepEqual(r.terms, [
    { term: "idempotent", meaning: "same result if repeated" },
    { term: "retry", meaning: "send again :: after a timeout" },
  ]);
});

test("works while streaming and without a native section", () => {
  assert.equal(parseSections("Half an explan").simple, "Half an explan");
  assert.equal(parseSections("Done.\n<<<NAT").simple, "Done.");
  const r = parseSections("Only English.\n<<<TERMS>>>\nCAP :: consistency vs availability");
  assert.equal(r.native, "");
  assert.equal(r.terms.length, 1);
});
