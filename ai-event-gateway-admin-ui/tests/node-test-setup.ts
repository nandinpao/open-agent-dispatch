import { webcrypto } from 'node:crypto';

if (!globalThis.crypto?.getRandomValues) {
  Object.defineProperty(globalThis, 'crypto', {
    configurable: true,
    enumerable: false,
    value: webcrypto,
    writable: true,
  });
}
