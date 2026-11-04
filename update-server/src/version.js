const TAG = /^v?(\d+)\.(\d+)\.(\d+)(?:[-+].*)?$/;

/**
 * Turns a release tag such as "v1.2.3" into the version the app compares.
 *
 * versionCode = major * 10000 + minor * 100 + patch, so v1.2.3 is 10203. The release workflow
 * (.github/workflows/release.yml) uses the same formula to build the APK, so the number here is the
 * number inside the APK. Minor and patch must stay below 100.
 *
 * Returns null for tags that do not follow the pattern.
 */
export function parseTag(tag) {
  const match = TAG.exec(String(tag).trim());
  if (!match) return null;
  const [major, minor, patch] = match.slice(1).map(Number);
  if (minor > 99 || patch > 99) return null;
  return {
    versionName: `${major}.${minor}.${patch}`,
    versionCode: major * 10000 + minor * 100 + patch,
  };
}

/** Whether [tag] is safe to use in a file name and in a URL. */
export function isSafeTag(tag) {
  return typeof tag === 'string' && /^v?\d+\.\d+\.\d+[0-9A-Za-z.+-]*$/.test(tag) && tag.length < 64;
}
