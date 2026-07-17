#!/usr/bin/env node
import { spawn } from 'node:child_process';
import { createRequire } from 'node:module';
import process from 'node:process';

const require = createRequire(import.meta.url);

let nextCli;
try {
  nextCli = require.resolve('next/dist/bin/next');
} catch {
  console.error('Cannot find Next.js CLI in node_modules.');
  console.error('Run `npm ci` from the project root, then run `npm run build && npm run start` again.');
  process.exit(1);
}

const args = ['start', ...process.argv.slice(2)];

const child = spawn(process.execPath, [nextCli, ...args], {
  stdio: 'inherit',
  env: {
    ...process.env,
    NEXT_TELEMETRY_DISABLED: process.env.NEXT_TELEMETRY_DISABLED ?? '1'
  }
});

child.on('error', (error) => {
  console.error(error);
  process.exit(1);
});

child.on('exit', (code, signal) => {
  if (signal) {
    console.error(`next start terminated by signal ${signal}.`);
    process.exit(1);
  }
  process.exit(code ?? 0);
});
