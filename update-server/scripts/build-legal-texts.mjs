// Writes src/legal-texts.json from the app's string resources (the single source the app ships with).
// Run from update-server: node scripts/build-legal-texts.mjs
import { readFile, writeFile } from 'node:fs/promises';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';
import { LEGAL_TEXTS_FILE, LEGAL_VERSION, legalSectionsFromStrings } from '../src/legal.js';

const here = dirname(fileURLToPath(import.meta.url));
const resources = join(here, '..', '..', 'app', 'src', 'main', 'res');
const languages = { de: 'values', en: 'values-en' };

const texts = { version: LEGAL_VERSION, languages: {} };
for (const [language, folder] of Object.entries(languages)) {
  const xml = await readFile(join(resources, folder, 'strings.xml'), 'utf8');
  texts.languages[language] = legalSectionsFromStrings(xml);
}
await writeFile(LEGAL_TEXTS_FILE, `${JSON.stringify(texts, null, 2)}\n`, 'utf8');
console.log(`wrote ${LEGAL_TEXTS_FILE} (version ${LEGAL_VERSION})`);
