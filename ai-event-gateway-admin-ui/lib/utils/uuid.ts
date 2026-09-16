export interface UuidCryptoProvider {
  randomUUID?: () => string;
  getRandomValues?: <T extends ArrayBufferView | null>(array: T) => T;
}

let fallbackCounter = 0;

function formatUuidV4(bytes: Uint8Array): string {
  bytes[6] = (bytes[6] & 0x0f) | 0x40;
  bytes[8] = (bytes[8] & 0x3f) | 0x80;
  const hex = Array.from(bytes, (value) => value.toString(16).padStart(2, '0')).join('');
  return `${hex.slice(0, 8)}-${hex.slice(8, 12)}-${hex.slice(12, 16)}-${hex.slice(16, 20)}-${hex.slice(20)}`;
}

function fillFallbackBytes(bytes: Uint8Array): void {
  fallbackCounter = (fallbackCounter + 1) >>> 0;
  let state = (Date.now() ^ fallbackCounter ^ Math.floor(Math.random() * 0xffffffff)) >>> 0;
  for (let index = 0; index < bytes.length; index += 1) {
    state ^= state << 13;
    state ^= state >>> 17;
    state ^= state << 5;
    bytes[index] = state & 0xff;
  }
}

/**
 * Creates an RFC 4122 version 4 UUID across browsers, secure and non-secure
 * contexts, Node.js rendering, and test environments.
 */
export function createUuid(provider: UuidCryptoProvider | undefined = globalThis.crypto): string {
  if (typeof provider?.randomUUID === 'function') {
    try {
      return provider.randomUUID();
    } catch {
      // Continue with getRandomValues when randomUUID is present but unavailable.
    }
  }

  const bytes = new Uint8Array(16);
  if (typeof provider?.getRandomValues === 'function') {
    try {
      provider.getRandomValues(bytes);
      return formatUuidV4(bytes);
    } catch {
      // Continue with a non-cryptographic compatibility fallback.
    }
  }

  fillFallbackBytes(bytes);
  return formatUuidV4(bytes);
}

export function createIdempotencyKey(prefix?: string): string {
  const uuid = createUuid();
  return prefix ? `${prefix}:${uuid}` : uuid;
}
