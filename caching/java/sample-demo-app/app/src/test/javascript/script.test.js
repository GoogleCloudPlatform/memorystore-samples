/*
 * Copyright 2026 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

const assert = require("node:assert/strict");
const fs = require("node:fs");
const path = require("node:path");
const test = require("node:test");
const vm = require("node:vm");

const SCRIPT_PATH = path.resolve(
  __dirname,
  "../../main/resources/static/script.js"
);
const SCRIPT_SOURCE = fs.readFileSync(SCRIPT_PATH, "utf8");

function makeElement() {
  return {
    value: "",
    disabled: false,
    textContent: "",
    innerHTML: "",
    className: "",
    dataset: {},
    style: { display: "block" },
    children: [],
    addEventListener() {},
    appendChild(child) {
      this.children.push(child);
    },
    querySelector() {
      return makeElement();
    },
    querySelectorAll() {
      return this.children;
    },
    remove() {},
  };
}

function loadScript(fetchImpl = async () => ({ ok: true, status: 200, json: async () => ({}) })) {
  const elements = new Map();
  const getEl = (id) => {
    if (!elements.has(id)) {
      elements.set(id, makeElement());
    }
    return elements.get(id);
  };

  const sandbox = {
    window: {
      location: { origin: "http://localhost:8080" },
      addEventListener() {},
    },
    document: {
      getElementById: getEl,
      createElement: () => makeElement(),
    },
    fetch: fetchImpl,
    console: { log() {}, error() {} },
    Date,
    encodeURIComponent,
    isNaN,
    parseFloat,
    Array,
    String,
    Error,
  };

  vm.createContext(sandbox);
  vm.runInContext(SCRIPT_SOURCE, sandbox, { filename: "script.js" });
  return { sandbox, getEl };
}

test("escapeHtml escapes HTML metacharacters and handles nullish inputs", () => {
  const { sandbox } = loadScript();
  assert.equal(
    sandbox.escapeHtml(`<script>alert("xss" & '1')</script>`),
    "&lt;script&gt;alert(&quot;xss&quot; &amp; &#39;1&#39;)&lt;/script&gt;"
  );
  assert.equal(sandbox.escapeHtml(null), "");
  assert.equal(sandbox.escapeHtml(undefined), "");
  assert.equal(sandbox.escapeHtml(42), "42");
});

test("addItemStyling escapes id, name, price, and description against stored XSS", () => {
  const { sandbox } = loadScript();
  const html = sandbox.addItemStyling({
    id: `1"><img src=x onerror=alert(1)>`,
    name: `<img src=x onerror=alert('name')>`,
    price: `9.99</span><script>evil()</script>`,
    description: `<svg onload="alert('desc')">`,
  });

  assert.doesNotMatch(html, /<img\b/i);
  assert.doesNotMatch(html, /<script\b/i);
  assert.doesNotMatch(html, /<svg\b/i);
  assert.match(html, /&lt;img src=x onerror=alert\(&#39;name&#39;\)&gt;/);
  assert.match(html, /&lt;svg onload=&quot;alert\(&#39;desc&#39;\)&quot;&gt;/);
});

test("search rejects blank input and URL-encodes item IDs", async () => {
  const requestedUrls = [];
  const { sandbox, getEl } = loadScript(async (url) => {
    requestedUrls.push(url);
    return {
      ok: true,
      status: 200,
      json: async () => ({
        id: 7,
        name: "Widget",
        price: 12.5,
        description: "Safe",
        fromCache: true,
      }),
    };
  });

  getEl("search-input").value = "   ";
  await sandbox.search();
  assert.equal(requestedUrls.length, 0);
  assert.equal(getEl("error-msg").textContent, "Please enter an item ID");

  getEl("search-input").value = "42/../admin?debug=1";
  await sandbox.search();
  assert.deepEqual(requestedUrls, [
    "http://localhost:8080/api/item/42%2F..%2Fadmin%3Fdebug%3D1",
  ]);
});

test("apiRequest treats 400 validation failures as errors instead of valid items", async () => {
  const { sandbox, getEl } = loadScript(async () => ({
    ok: false,
    status: 400,
    json: async () => ({ error: "Bad Request" }),
  }));

  const result = await sandbox.apiRequest("/item/create", { method: "POST" });
  assert.equal(result, null);
  assert.equal(getEl("error-msg").textContent, "An error occurred");
});
