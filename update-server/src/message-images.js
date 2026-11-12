import { randomBytes } from 'node:crypto';
import { mkdir, readFile, rename, rm, writeFile } from 'node:fs/promises';
import { join } from 'node:path';

/**
 * Pictures the admin uploads for message sheets. Kept in DATA_DIR (an index file and one file per picture),
 * served at GET /v1/message-image/:id. Only PNG, JPEG and WebP are accepted, checked by their first bytes
 * and not by the name or the type the browser claims.
 */

export const MAX_IMAGE_BYTES = 2 * 1024 * 1024;
export const MAX_IMAGES = 30;
export const IMAGE_ID = /^[0-9a-f]{12}$/;

/** The image type of [buffer] from its first bytes, or undefined if it is not PNG, JPEG or WebP. */
export function sniffImage(buffer) {
  if (!Buffer.isBuffer(buffer) || buffer.length < 12) return undefined;
  if (buffer.subarray(0, 8).equals(Buffer.from([0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a]))) return 'image/png';
  if (buffer[0] === 0xff && buffer[1] === 0xd8 && buffer[2] === 0xff) return 'image/jpeg';
  if (buffer.subarray(0, 4).toString('latin1') === 'RIFF' && buffer.subarray(8, 12).toString('latin1') === 'WEBP') return 'image/webp';
  return undefined;
}

/**
 * Reads the one file of a multipart/form-data upload (field "file"). Returns { name, data } or undefined when
 * the body is not that. A small parser on purpose: the admin form has exactly one file field.
 */
export function parseSingleFile(body, contentType) {
  const boundary = /boundary=(?:"([^"]+)"|([^;\s]+))/i.exec(String(contentType ?? ''));
  if (!boundary || !Buffer.isBuffer(body)) return undefined;
  const delimiter = Buffer.from(`--${boundary[1] ?? boundary[2]}`);
  let position = body.indexOf(delimiter);
  while (position !== -1) {
    const start = position + delimiter.length;
    if (body.subarray(start, start + 2).toString() === '--') return undefined;
    const headerEnd = body.indexOf('\r\n\r\n', start);
    if (headerEnd === -1) return undefined;
    const headers = body.subarray(start, headerEnd).toString('utf8');
    const next = body.indexOf(delimiter, headerEnd + 4);
    if (next === -1) return undefined;
    const disposition = /content-disposition:[^\r\n]*/i.exec(headers)?.[0] ?? '';
    const field = /name="([^"]*)"/i.exec(disposition)?.[1];
    const filename = /filename="([^"]*)"/i.exec(disposition)?.[1];
    if (field === 'file' && filename !== undefined) {
      // The part ends with CRLF before the next delimiter.
      const data = body.subarray(headerEnd + 4, Math.max(headerEnd + 4, next - 2));
      return { name: filename.replace(/[^\w. -]/g, '').slice(0, 60) || 'image', data };
    }
    position = next;
  }
  return undefined;
}

export class MessageImageStore {
  constructor({ config }) {
    this.dir = join(config.dataDir, 'message-images');
    this.index = join(config.dataDir, 'message-images.json');
    this.images = undefined;
    this.queue = Promise.resolve();
  }

  async #load() {
    if (this.images) return this.images;
    try {
      const parsed = JSON.parse(await readFile(this.index, 'utf8'));
      this.images = Array.isArray(parsed.images) ? parsed.images.filter((image) => IMAGE_ID.test(image?.id)) : [];
    } catch {
      this.images = [];
    }
    return this.images;
  }

  async list() {
    return [...(await this.#load())];
  }

  async has(id) {
    return (await this.#load()).some((image) => image.id === id);
  }

  /** Stores an upload. Returns { image } or { error }: "no_file", "bad_type", "too_large" or "too_many". */
  add({ name, data }) {
    const task = this.queue.then(async () => {
      const images = await this.#load();
      if (!data || data.length === 0) return { error: 'no_file' };
      if (data.length > MAX_IMAGE_BYTES) return { error: 'too_large' };
      const type = sniffImage(data);
      if (!type) return { error: 'bad_type' };
      if (images.length >= MAX_IMAGES) return { error: 'too_many' };
      const id = randomBytes(6).toString('hex');
      await mkdir(this.dir, { recursive: true });
      await writeFile(join(this.dir, id), data);
      const image = { id, name, type, size: data.length, at: new Date().toISOString() };
      images.unshift(image);
      await this.#save();
      return { image };
    });
    this.queue = task.catch(() => {});
    return task;
  }

  /** The picture's type and bytes, or undefined. */
  async get(id) {
    if (!IMAGE_ID.test(id)) return undefined;
    const image = (await this.#load()).find((entry) => entry.id === id);
    if (!image) return undefined;
    try {
      return { type: image.type, data: await readFile(join(this.dir, id)) };
    } catch {
      return undefined;
    }
  }

  remove(id) {
    const task = this.queue.then(async () => {
      const images = await this.#load();
      const before = images.length;
      this.images = images.filter((image) => image.id !== id);
      if (!IMAGE_ID.test(id) || this.images.length === before) return false;
      await rm(join(this.dir, id), { force: true });
      await this.#save();
      return true;
    });
    this.queue = task.catch(() => {});
    return task;
  }

  async #save() {
    await mkdir(this.dir, { recursive: true });
    const temporary = `${this.index}.tmp`;
    await writeFile(temporary, JSON.stringify({ version: 1, images: this.images }), 'utf8');
    await rename(temporary, this.index);
  }
}
