import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import { dirname, join } from 'node:path';
import { test } from 'node:test';
import { fileURLToPath } from 'node:url';
import { LEGAL_KEYS, LEGAL_VERSION, legalAnswer, legalSectionsFromStrings, loadLegalTexts, unescapeAndroid } from '../src/legal.js';
import { buildServer } from '../src/server.js';
import { testConfig } from './helpers.js';

const HERE = dirname(fileURLToPath(import.meta.url));
const APP = join(HERE, '..', '..', 'app', 'src', 'main');
const host = { host: 'updates.example.com', 'x-forwarded-proto': 'https' };

test('the server texts are the app texts (German and English), section by section', async (t) => {
  const texts = await loadLegalTexts();
  for (const [language, folder] of [['de', 'values'], ['en', 'values-en']]) {
    let xml;
    try {
      xml = await readFile(join(APP, 'res', folder, 'strings.xml'), 'utf8');
    } catch {
      t.skip('the app resources are not in this checkout');
      return;
    }
    assert.deepEqual(texts.languages[language], legalSectionsFromStrings(xml), `legal-texts.json (${language}) differs from the app; run scripts/build-legal-texts.mjs`);
  }
});

test('the server version is the app version', async (t) => {
  let kotlin;
  try {
    kotlin = await readFile(join(APP, 'java', 'com', 'bdersolutions', 'daybreak', 'data', 'legal', 'LegalTerms.kt'), 'utf8');
  } catch {
    t.skip('the app sources are not in this checkout');
    return;
  }
  assert.equal(Number(/const val VERSION = (\d+)/.exec(kotlin)[1]), LEGAL_VERSION);
});

test('the sections keep the app order and every section has a title and a body', async () => {
  const texts = await loadLegalTexts();
  for (const language of ['de', 'en']) {
    assert.deepEqual(texts.languages[language].map((section) => section.key), LEGAL_KEYS);
    for (const section of texts.languages[language]) {
      assert.ok(section.title && section.body, `${language} ${section.key}`);
    }
  }
});

test('Android escapes are read the way the app shows them', () => {
  assert.equal(unescapeAndroid('Zeile\nzwei'), 'Zeile\nzwei');
  assert.equal(unescapeAndroid('Daybreak\’s &amp; co &lt;3&gt;'), 'Daybreak’s & co <3>');
  assert.equal(unescapeAndroid(String.raw`it\'s "quoted"`), `it's "quoted"`);
});

test('GET /v1/legal serves the texts in German by default and in English on request', async () => {
  const config = await testConfig({ adminToken: 'admin-secret-long-enough' });
  const { app } = buildServer({ config });
  const de = await app.inject({ method: 'GET', url: '/v1/legal', headers: host });
  assert.equal(de.statusCode, 200);
  assert.equal(de.json().version, LEGAL_VERSION);
  assert.equal(de.json().lang, 'de');
  assert.equal(de.json().sections.length, LEGAL_KEYS.length);
  assert.match(de.headers['cache-control'], /max-age=3600/);

  const en = await app.inject({ method: 'GET', url: '/v1/legal?lang=en', headers: host });
  assert.equal(en.json().lang, 'en');
  assert.deepEqual(en.json(), legalAnswer(await loadLegalTexts(), 'en'));
});
