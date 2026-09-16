import assert from 'node:assert/strict';
import test from 'node:test';
import { paginateItems, recordIncludesQuery, uniqueSortedValues } from '@/lib/utils/list';

test('paginateItems clamps page range and returns visible bounds', () => {
  const result = paginateItems([1, 2, 3, 4, 5], { page: 99, pageSize: 2 });

  assert.deepEqual(result.items, [5]);
  assert.equal(result.page, 3);
  assert.equal(result.totalPages, 3);
  assert.equal(result.startItem, 5);
  assert.equal(result.endItem, 5);
});

test('paginateItems reports empty result without invalid item range', () => {
  const result = paginateItems([], { page: 1, pageSize: 10 });

  assert.deepEqual(result.items, []);
  assert.equal(result.totalItems, 0);
  assert.equal(result.totalPages, 1);
  assert.equal(result.startItem, 0);
  assert.equal(result.endItem, 0);
});

test('recordIncludesQuery performs case-insensitive multi-field matching', () => {
  assert.equal(recordIncludesQuery(['agent-001', 'OPENCLAW', 'node-a'], 'openclaw'), true);
  assert.equal(recordIncludesQuery(['agent-001', 'OPENCLAW', 'node-a'], 'node-b'), false);
});

test('uniqueSortedValues removes empty values and sorts unique labels', () => {
  assert.deepEqual(uniqueSortedValues(['node-b', undefined, 'node-a', 'node-b', null, '']), ['node-a', 'node-b']);
});
