import assert from 'node:assert/strict';
import { test } from 'node:test';
import { escapeHtml, formatSize, pickLanguage, renderChangelog } from '../src/page.js';

test('the language follows the first preference of the browser', () => {
  assert.equal(pickLanguage('de-DE,de;q=0.9,en;q=0.8'), 'de');
  assert.equal(pickLanguage('en-GB,de;q=0.5'), 'en');
  assert.equal(pickLanguage(undefined), 'en');
  assert.equal(pickLanguage(''), 'en');
});

test('release notes become headings, bullets and paragraphs', () => {
  const html = renderChangelog('## What\'s new\r\n- one\r\n- two\r\n\r\nSome text');

  assert.equal(html, "<h4>What&#39;s new</h4>\n<ul>\n<li>one</li>\n<li>two</li>\n</ul>\n<p>Some text</p>");
});

test('links are https only and keep trailing punctuation outside', () => {
  const html = renderChangelog('See https://example.com/a?b=1&c=2. Or javascript:alert(1)');

  assert.match(html, /<a href="https:\/\/example\.com\/a\?b=1&amp;c=2" rel="noopener noreferrer">/);
  assert.ok(!html.includes('href="javascript'));
});

test('markup is escaped', () => {
  assert.equal(escapeHtml('<a href="x">&\'</a>'), '&lt;a href=&quot;x&quot;&gt;&amp;&#39;&lt;/a&gt;');
});

test('sizes are shown in megabytes', () => {
  assert.equal(formatSize(5 * 1024 * 1024), '5.0 MB');
  assert.equal(formatSize(0), '');
  assert.equal(formatSize(undefined), '');
});
