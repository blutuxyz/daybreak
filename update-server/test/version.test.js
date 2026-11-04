import assert from 'node:assert/strict';
import { test } from 'node:test';
import { isSafeTag, parseTag } from '../src/version.js';

test('a tag becomes a version name and a version code', () => {
  assert.deepEqual(parseTag('v1.2.3'), { versionName: '1.2.3', versionCode: 10203 });
  assert.deepEqual(parseTag('1.0.0'), { versionName: '1.0.0', versionCode: 10000 });
  assert.deepEqual(parseTag('v12.34.56'), { versionName: '12.34.56', versionCode: 123456 });
});

test('a newer tag always has a higher version code', () => {
  const codes = ['v1.0.9', 'v1.1.0', 'v1.1.1', 'v2.0.0'].map((tag) => parseTag(tag).versionCode);
  assert.deepEqual(codes, [...codes].sort((a, b) => a - b));
});

test('a suffix after the version is ignored', () => {
  assert.equal(parseTag('v1.2.3-rc.1').versionCode, 10203);
});

test('tags that are not versions, or do not fit the formula, are rejected', () => {
  assert.equal(parseTag('nightly'), null);
  assert.equal(parseTag('v1.2'), null);
  assert.equal(parseTag('v1.100.0'), null);
  assert.equal(parseTag('v1.0.100'), null);
});

test('only plain tags are safe for file names and URLs', () => {
  assert.equal(isSafeTag('v1.2.3'), true);
  assert.equal(isSafeTag('v1.2.3-rc.1'), true);
  assert.equal(isSafeTag('../etc/passwd'), false);
  assert.equal(isSafeTag('v1.2.3/../x'), false);
  assert.equal(isSafeTag(undefined), false);
});
