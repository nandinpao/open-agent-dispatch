#!/usr/bin/env node
import { createRequire } from 'node:module';
import process from 'node:process';

const require = createRequire(import.meta.url);
const requiredPackages = [
  ['next', 'next/package.json'],
  ['tsx', 'tsx/package.json'],
  ['typescript', 'typescript/package.json'],
  ['eslint', 'eslint/package.json']
];

const missing = [];

for (const [name, specifier] of requiredPackages) {
  try {
    require.resolve(specifier);
  } catch {
    missing.push(name);
  }
}

if (missing.length > 0) {
  console.error('Missing local npm dependencies: ' + missing.join(', '));
  console.error('');
  console.error('Please install dependencies from the project root before running dev/tests/build:');
  console.error('  rm -rf node_modules .next');
  console.error('  npm ci');
  console.error('');
  console.error('If npm ci fails because package-lock.json was edited locally, use:');
  console.error('  npm install');
  console.error('  npm run typecheck');
  console.error('  npm run test:normalizers');
  console.error('  npm run dev');
  process.exit(1);
}
