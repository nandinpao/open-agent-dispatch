import assert from 'node:assert/strict';
import test from 'node:test';
import { createIdempotencyKey, createUuid, type UuidCryptoProvider } from '../lib/utils/uuid';

const UUID_V4 = /^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/;

test('uses crypto.randomUUID when available', () => {
  const expected = '12345678-1234-4234-9234-123456789abc';
  const provider: UuidCryptoProvider = { randomUUID: () => expected };
  assert.equal(createUuid(provider), expected);
});

test('uses getRandomValues when randomUUID is unavailable', () => {
  const provider: UuidCryptoProvider = {
    getRandomValues: (array) => {
      const bytes = array as Uint8Array;
      for (let index = 0; index < bytes.length; index += 1) bytes[index] = index;
      return array;
    },
  };
  const value = createUuid(provider);
  assert.match(value, UUID_V4);
  assert.equal(value, '00010203-0405-4607-8809-0a0b0c0d0e0f');
});

test('falls back without a Web Crypto provider', () => {
  const first = createUuid(undefined);
  const second = createUuid(undefined);
  assert.match(first, UUID_V4);
  assert.match(second, UUID_V4);
  assert.notEqual(first, second);
});

test('creates namespaced idempotency keys', () => {
  assert.match(createIdempotencyKey('login'), /^login:[0-9a-f-]{36}$/);
  assert.match(createIdempotencyKey(), UUID_V4);
});
